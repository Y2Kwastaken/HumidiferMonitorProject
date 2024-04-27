#include "../lib/debug_helper.h"
#include <Arduino.h>

int DO_DEBUG = 0;

void setupDebug(int serial)
{
    if (DO_DEBUG == 1)
    {
        Serial.begin(serial);
    }
}

void sendDebug(const Printable &text)
{
    if (DO_DEBUG == 1)
    {
        Serial.print(text);
    }
}

void sendDebugLine(const Printable &text)
{
    if (DO_DEBUG == 1)
    {
        Serial.println(text);
    }
}

void sendDebug(const char* text)
{
    if (DO_DEBUG == 1)
    {
        Serial.print(text);
    }
}

void sendDebugLine(const char* text)
{
    if (DO_DEBUG == 1)
    {
        Serial.println(text);
    }
}
