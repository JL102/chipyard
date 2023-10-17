#include <stdio.h>
#include "sage.h"

#define NUM_CORES 4

int __main(void) {

  // Get the id of the current processor
  unsigned long hartid = 0;
  asm volatile ("csrr %0, mhartid" : "=r"(hartid));

  // Send message to hart 0, encoding this hartid and i in the message
  for (int i = 0; i < 8; i++) {
    qPut(0, 100*hartid + i);
  }

  while(1);
}

int main(void) {
  int sourceID, message, pollBits;

  // Wait for the first message
  qWaitAny();
  pollBits = qPollAll();

  while (pollBits != 0) {
    // pollBits is a 4 bit word, 1 for each hart, where 1 means a message is present in the given hart.
    // Find the index of the first bit that is 1, and set that to sourceID
    sourceID = __builtin_ffs(pollBits) - 1;
    message = qGet(sourceID);
    printf("Received message from hart %d: %d, pollBits=%#x\n", sourceID, message, pollBits);
    pollBits = qPollAll();

  }

  return 0;

  // // Wait for the first message
  // sourceID = qWaitAny();
  // message = qGet(sourceID);
  // pollBits = qPollAll();
  // printf("Step 1: Received message from hart %d: %d, pollBits=%#x\n", sourceID, message, pollBits);
  // 
  // // 
  //
  // // Wait for another message from the same hart
  // int newSourceId = qWait(sourceID);
  // if (newSourceId != sourceID) {
  //   printf("Error: qWait returned wrong sourceID: %d, expected %d\n", newSourceId, sourceID);
  //   return 1;
  // }
  // message = qGet(sourceID);
  // pollBits = qPollAll();
  // printf("Step 2: Received message from hart %d: %d, pollBits=%#x\n", sourceID, message, pollBits);
  //
  // return 0;
}
