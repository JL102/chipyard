#include "rocc.h"
#include <stdio.h>

#define FUNCT_Q     0b1000000;
#define FUNCT_M     0b0000000;
// Two bits in the middle not defined yet
#define FUNCT_PUT   0b0001000;
#define FUNCT_WAIT  0b0000100;
#define FUNCT_GET   0b0000010;
#define FUNCT_POLL  0b0000001;

#define FUNCT_MPUT   0b0001000;
#define FUNCT_MWAIT  0b0000100;
#define FUNCT_MGET   0b0000010;
#define FUNCT_MPOLL  0b0000001;

#define FUNCT_QPUT   0b1001000;
#define FUNCT_QWAIT  0b1000100;
#define FUNCT_QGET   0b1000010;
#define FUNCT_QPOLL  0b1000001;

// #define FUNCT_QPUT   = (FUNCT_Q | FUNCT_PUT);
// #define FUNCT_QWAIT  = (FUNCT_Q | FUNCT_WAIT);
// #define FUNCT_QGET   = (FUNCT_Q | FUNCT_GET);
// #define FUNCT_QPOLL  = (FUNCT_Q | FUNCT_POLL);
//
// #define FUNCT_MPUT   = (FUNCT_M | FUNCT_PUT);
// #define FUNCT_MWAIT  = (FUNCT_M | FUNCT_WAIT);
// #define FUNCT_MGET   = (FUNCT_M | FUNCT_GET);
// #define FUNCT_MPOLL  = (FUNCT_M | FUNCT_POLL);

// #define CUSTOM_INSTR 0; // instruction # (could be 0 to 6 I think)

// QUEUE OPERATIONS
static inline void qPut(int destination, unsigned long data) {
  ROCC_INSTRUCTION_SS(0, destination, data, FUNCT_QPUT);
}

static inline unsigned long qWaitAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(0, result, FUNCT_QWAIT);
  return result;
}

static inline unsigned long qGetAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(0, result, FUNCT_QGET);
  return result;
}

static inline unsigned int qPollAny() {
  unsigned int result; // 0 or 1
  ROCC_INSTRUCTION_D(0, result, FUNCT_QPOLL);
  return result;
}

// MEMORY OPERATIONS
static inline void mPut(int *destination, unsigned long data) {
  ROCC_INSTRUCTION_SS(0, destination, data, 0b0001000);
}

static inline unsigned long mWaitAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(0, result, FUNCT_MWAIT);
  return result;
}

static inline unsigned long mGetAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(0, result, FUNCT_MGET);
  return result;
}

static inline unsigned int mPollAny() {
  unsigned int result; // 0 or 1
  ROCC_INSTRUCTION_D(0, result, FUNCT_MPOLL);
  return result;
}

// Note: if we want to add immediate variants, we can use bits 1 and 2 to differentiate
// in the funct7, and use macros like ROCC_INSTRUCTION_R_I_I instead of ROCC_INSTRUCTION_D_S_S

int main(void) {

  int x = 98473523;

  int destination;

  mPut(&destination, x);
  
  printf("Used mPut to send the number %d into the destination int\n", destination);
}
