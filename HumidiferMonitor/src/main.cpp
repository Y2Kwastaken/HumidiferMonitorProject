#include <Arduino.h>
#include <SoftwareSerial.h>
#include <string.h>
#include "../lib/debug_helper.h"

#define SERIAL_COMMUNICATION 115200
#define DEBUG 0
#define DEBUG_PIN 11
#define DEBUG_SERIAL 9600

#define WATER_THRESHOLD 10

int half_seconds = 0;
const int check_after_seconds = 2;

SoftwareSerial espSerial(7, 6);

void timer_setup();

ISR(TIMER1_OVF_vect)
{
  TCNT1 = 34285;
  ++half_seconds;

  if (half_seconds * 2 == check_after_seconds)
  {
    half_seconds = 0;
    uint8_t buffer[1] = {analogRead(A0)};
    char str[3];
    itoa(buffer[0], str, 10);
    sendDebug("Water Sensor: ");
    sendDebugLine(str);
    espSerial.print(str);
  }
}

void setup()
{
  DO_DEBUG = DEBUG;
  setupDebug(DEBUG_SERIAL);
  espSerial.begin(115200);
  timer_setup();

  pinMode(A0, INPUT);
}

void loop()
{
}

void timer_setup()
{
  // 500ms = (64 * ticks)/16,000,000
  TCCR1A = 0;
  TCCR1B = 0b00000101;
  TCNT1 |= 34285;
  TIMSK1 |= 0b00000001;
}