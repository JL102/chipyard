#include "rocc.h"
#include <stdio.h>

#define FUNCT_Q     0b0000000
#define FUNCT_M     0b1000000
// Two bits in the middle not defined yet
#define FUNCT_PUT   0b0001000
#define FUNCT_WAIT  0b0000100
#define FUNCT_GET   0b0000010
#define FUNCT_POLL  0b0000001

#define FUNCT_QPUT    (FUNCT_Q | FUNCT_PUT)
#define FUNCT_QWAIT   (FUNCT_Q | FUNCT_WAIT)
#define FUNCT_QGET    (FUNCT_Q | FUNCT_GET)
#define FUNCT_QPOLL   (FUNCT_Q | FUNCT_POLL)


#define FUNCT_MPUT    (FUNCT_M | FUNCT_PUT)
#define FUNCT_MWAIT   (FUNCT_M | FUNCT_WAIT)
#define FUNCT_MGET    (FUNCT_M | FUNCT_GET)
#define FUNCT_MPOLL   (FUNCT_M | FUNCT_POLL)

#define CUSTOM_INSTR 0 // instruction # (could be 0 to 6 I think)

// MEMORY OPERATIONS
static inline void mPut(int *destination, unsigned long data) {
  ROCC_INSTRUCTION_SS(CUSTOM_INSTR, destination, data, FUNCT_MPUT);
}

static inline unsigned long mWaitAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(CUSTOM_INSTR, result, FUNCT_MWAIT);
  return result;
}

static inline unsigned long mGetAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(CUSTOM_INSTR, result, FUNCT_MGET);
  return result;
}

static inline unsigned int mPollAny() {
  unsigned int result; // 0 or 1
  ROCC_INSTRUCTION_D(CUSTOM_INSTR, result, FUNCT_MPOLL);
  return result;
}

// QUEUE OPERATIONS
static inline void qPut(unsigned long destination, unsigned long data) {
  ROCC_INSTRUCTION_SS(CUSTOM_INSTR, destination, data, FUNCT_QPUT);
}

static inline unsigned long qWaitAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(CUSTOM_INSTR, result, FUNCT_QWAIT);
  return result;
}

static inline unsigned long qGetAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(CUSTOM_INSTR, result, FUNCT_QGET);
  return result;
}

static inline unsigned int qPollAny() {
  unsigned int result; // 0 or 1
  ROCC_INSTRUCTION_D(CUSTOM_INSTR, result, FUNCT_QPOLL);
  return result;
}

// Note: if we want to add immediate variants, we can use bits 1 and 2 to differentiate
// in the funct7, and use macros like ROCC_INSTRUCTION_R_I_I instead of ROCC_INSTRUCTION_D_S_S

#define NUM_CORES 4

// This function runs on all non-zero harts
int __main(void) {
  // Get the id of the current processor
  unsigned long hartid = 0;
  asm volatile ("csrr %0, mhartid" : "=r"(hartid));

  // Figure out what hart to pass the ball to
  int myNextNeighbor = hartid + 1;
  if (myNextNeighbor == NUM_CORES) myNextNeighbor = 0;
  // wrote this a different way to avoid int underflow
  int myPrevNeighbor = (hartid == 0) ? NUM_CORES : hartid - 1;

  int ball, sourceID;

  // In a loop, pass the ball over and over and increment each time
  while (1) {
    sourceID = qWaitAny();
    // Sanity check - Make sure the message came from the prev neighbor
    if (sourceID != myPrevNeighbor) {
      // Create a recognizable exit code
      return 100 * sourceID + 10 * myPrevNeighbor + hartid;
    }
    ball = qGetAny();
    
    qPut(myNextNeighbor, ball + 1);
  }
}

// This function runs on hart0
int main(void) {

  // hartid should be 0, and next/prev neighbors can be hardcoded
  int myNextNeighbor = 1;
  int myPrevNeighbor = NUM_CORES - 1;
  
  int ball, sourceID;

  ball = 100;
  printf("Started the ball rolling with value %d sent to core %d\n", ball, myNextNeighbor);
  qPut(myNextNeighbor, ball);

  for (int i = 0; i < 100; i++) {
    sourceID = qWaitAny();
    // Sanity check - different exit code than the __main loop
    if (sourceID != myPrevNeighbor) {
      return 100 * sourceID + 10 * myPrevNeighbor;
    }
    ball = qGetAny();
    printf("Received ball value %d from core %d\n", ball, sourceID);
    qPut(myNextNeighbor, ball + 1);
  }
}
