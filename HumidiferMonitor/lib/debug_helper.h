#include <Printable.h>

extern int DO_DEBUG;

void setupDebug(int serialPort);
void sendDebugLine(const Printable &);
void sendDebug(const Printable &);
void sendDebugLine(const char* text);
void sendDebug(const char* text);