#include "rocc.h"

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

static inline unsigned long qWait(unsigned long source) {
  unsigned long result; // data
  ROCC_INSTRUCTION_DS(CUSTOM_INSTR, result, source, FUNCT_QWAIT);
  return result;
}

static inline unsigned long qGetAny() {
  unsigned long result; // data
  ROCC_INSTRUCTION_D(CUSTOM_INSTR, result, FUNCT_QGET);
  return result;
}

static inline unsigned long qGet(unsigned long source) {
  unsigned long result; // data
  ROCC_INSTRUCTION_DS(CUSTOM_INSTR, result, source, FUNCT_QGET);
  return result;
}

static inline unsigned int qPollAll() {
  unsigned int result; // 0 or 1
  ROCC_INSTRUCTION_D(CUSTOM_INSTR, result, FUNCT_QPOLL);
  return result;
}

static inline unsigned int qPoll(unsigned long source) {
  unsigned int result; // 0 or 1
  ROCC_INSTRUCTION_DS(CUSTOM_INSTR, source, result, FUNCT_QPOLL);
  return result;
}

