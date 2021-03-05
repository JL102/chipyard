package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer, HashMap}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.{ResetCatchAndSync}
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters, ClockSinkParameters, ClockParameters}

import chipyard.harness.{ApplyHarnessBinders, HarnessBinders}
import chipyard.iobinders.HasIOBinders
import chipyard.clocking.{SimplePllConfiguration, ClockDividerN}

// -------------------------------
// Chipyard Test Harness
// -------------------------------

case object BuildTop extends Field[Parameters => LazyModule]((p: Parameters) => new ChipTop()(p))

trait HasTestHarnessFunctions {
  val harnessFunctions = ArrayBuffer.empty[HasHarnessSignalReferences => Seq[Any]]
}

trait HasHarnessSignalReferences {
  def harnessClock: Clock
  def harnessReset: Reset
  def dutReset: Reset
  def success: Bool
}

class HarnessClockInstantiator {
  private var _clockMap: HashMap[String, (Double, ClockBundle)] = HashMap.empty

  // request a clock bundle at a particular frequency
  def getClockBundleWire(name: String, freqRequested: Double): ClockBundle = {
    val clockBundle = Wire(new ClockBundle(ClockBundleParameters()))
    _clockMap(name) = (freqRequested, clockBundle)
    clockBundle
  }

  // connect all clock wires specified to a divider only PLL
  def instantiateHarnessDividerPLL(refClock: ClockBundle): Unit = {
    val sinks = _clockMap.map({ case (name, (freq, bundle)) =>
      ClockSinkParameters(take=Some(ClockParameters(freqMHz=freq/1000000)),name=Some(name))
    }).toSeq

    val pllConfig = new SimplePllConfiguration("harnessDividerOnlyClockGenerator", sinks)
    pllConfig.emitSummaries()

    val dividedClocks = HashMap[Int, Clock]()
    def instantiateDivider(div: Int): Clock = {
      val divider = Module(new ClockDividerN(div))
      divider.suggestName(s"ClockDivideBy${div}")
      divider.io.clk_in := refClock.clock
      dividedClocks(div) = divider.io.clk_out
      divider.io.clk_out
    }

    // connect wires to clock source
    for (sinkParams <- sinks) {
      val div = pllConfig.sinkDividerMap(sinkParams)
      val divClock = dividedClocks.getOrElse(div, instantiateDivider(div))
      _clockMap(sinkParams.name.get)._2.clock := divClock
      _clockMap(sinkParams.name.get)._2.reset := ResetCatchAndSync(divClock, refClock.reset.asBool)
    }
  }
}

case object HarnessClockInstantiatorKey extends Field[HarnessClockInstantiator](new HarnessClockInstantiator)

class TestHarness(implicit val p: Parameters) extends Module with HasHarnessSignalReferences {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val lazyDut = LazyModule(p(BuildTop)(p)).suggestName("chiptop")
  val dut = Module(lazyDut.module)
  io.success := false.B

  val (harnessClock, harnessReset, dutReset) = {
    val freqMHz = p(ReferenceClockTrackerKey).get.getOrElse(100.0) // default to 100MHz
    val bundle = p(HarnessClockInstantiatorKey).getClockBundleWire("implicit_harness_clock", freqMHz*1000000.0)
    (bundle.clock, WireInit(bundle.reset), bundle.reset.asAsyncReset)
  }
  val success = io.success

  lazyDut match { case d: HasTestHarnessFunctions =>
    d.harnessFunctions.foreach(_(this))
  }
  lazyDut match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }

  val implicitHarnessClockBundle = Wire(new ClockBundle(ClockBundleParameters()))
  implicitHarnessClockBundle.clock := clock
  implicitHarnessClockBundle.reset := reset
  p(HarnessClockInstantiatorKey).instantiateHarnessDividerPLL(implicitHarnessClockBundle)
}

