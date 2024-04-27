#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>
#include <WiFiUdp.h>
#include <string>
#include <SoftwareSerial.h>
#include "../lib/debug_helper.h"

WiFiServer server(27015);
WiFiClient client;

SoftwareSerial arduino(2, 0);

const char *network_ssid = DEFAULT_SSID;
const char *network_password = DEFAULT_PASS;

void openConnection();

void setup()
{
  DO_DEBUG = 1;
  setupDebug(74880);
  openConnection();
  server.begin();
  arduino.begin(115200);
  MDNS.begin("HumidifierMonitor");
}

void loop()
{
  MDNS.update();

  if (server.hasClient())
  {
    client = server.accept();
    sendDebugLine("Accepted Client");
  }

  String arduinoSensor = arduino.readString();
  if (arduinoSensor.isEmpty() || arduinoSensor.equals(" "))
  {
    return;
  }

  if (client.availableForWrite())
  {
    client.write(arduinoSensor.c_str());
  }
}

void openConnection()
{
  WiFi.begin(network_ssid, network_password);

  sendDebug("Connecting to ");
  sendDebugLine(network_ssid);

  while (WiFi.status() != WL_CONNECTED)
  {
    delay(500);
    sendDebug(".");
  }
  sendDebugLine("");
  sendDebug("Connected To IP: ");
  sendDebugLine(WiFi.localIP());
}