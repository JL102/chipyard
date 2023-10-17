#include <stdio.h>
#include "sage.h"

// Note: if we want to add immediate variants, we can use bits 1 and 2 to differentiate
// in the funct7, and use macros like ROCC_INSTRUCTION_R_I_I instead of ROCC_INSTRUCTION_D_S_S

#define NUM_CORES 4
#define DIRECTION 1

int getNextNeighbor(unsigned long hartid) {
  // clockwise - increase
  if (DIRECTION) {
    return (hartid == NUM_CORES - 1) ? 0 : hartid + 1;
  }
  else {
    return (hartid == 0) ? NUM_CORES - 1 : hartid - 1;
  }
}

int getPrevNeighbor(unsigned long hartid) {
  // clockwise - increase
  if (DIRECTION) {
    return (hartid == 0) ? NUM_CORES - 1 : hartid - 1;
  }
  else {
    return (hartid == NUM_CORES - 1) ? 0 : hartid + 1;
  }
}

// This function runs on all non-zero harts
int __main(void) {
  // Get the id of the current processor
  unsigned long hartid = 0;
  asm volatile ("csrr %0, mhartid" : "=r"(hartid));

  // Figure out what hart to pass the ball to
  int myNextNeighbor = getNextNeighbor(hartid);
  int myPrevNeighbor = getPrevNeighbor(hartid);

  int ball, sourceID;

  // In a loop, pass the ball over and over and increment each time
  while (1) {
    sourceID = qWaitAny();
    // Sanity check - Make sure the message came from the prev neighbor
    if (sourceID != myPrevNeighbor) {
      // Create a recognizable exit code
      return 100 * sourceID + 10 * myPrevNeighbor + hartid;
    }
    // ball = qGet(sourceID);
    ball = qGetAny();
    
    qPut(myNextNeighbor, ball + 1);
  }
}

// This function runs on hart0
int main(void) {

  // hartid should be 0, and next/prev neighbors can be hardcoded
  int myNextNeighbor = getNextNeighbor(0);
  int myPrevNeighbor = getPrevNeighbor(0);
  
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
    // ball = qGet(sourceID);
    ball = qGetAny();
    printf("Received ball value %d from core %d\n", ball, sourceID);
    qPut(myNextNeighbor, ball + 1);
  }
}
