# webCoRE_Proxy_App
 ## Description
 This Hubitat app allows you to execute webCoRE pistons via HTTP POST requests and receive custom data back in the response. Unlike standard piston execution which only returns a generic success message, this app waits for your piston to complete and returns whatever data you specify.

 ## Key Features:
 * Execute any piston dynamically by sending its ID in the POST body
 * Pass custom data to pistons (accessible as $args in webCoRE)
 * Receive custom responses from pistons as clean JSON
 * Automatic parsing of key:value pair format into proper JSON objects
 * Works both locally (same network) and remotely (via Hubitat cloud)
 * Configurable response variable name
 * OAuth secured

 ## Quick Setup:
 * Create a Virtual Switch in Hubitat (Devices → Add Device → Virtual → Virtual Switch)
 * Create a Global Variable in Hubitat (Settings → Hub Variables → Create New Variable)
 * Install this code in Apps Code
 * Enable OAuth for the app
 * Create an app instance and configure with your virtual switch, global variable and webCoRE details
 * Copy the URLs provided in the app settings
 * Setup Piston
**   (include Set Variable (e.g, @@ResponseData) and Virtual Switch Off at the end of the piston)
 * Copy Piston ID to include in Body of URL call

Your Piston Requirements:

Must turn OFF the designated virtual switch at the end (signals completion)
Store response data in a global string variable (default: @@ResponseData)
Format response as: Key1:Value1;Key2:Value2;Key3:Value3

* Example POST Request:
```json
{
  "pistonId": "your-piston-id",
  "data": {
    "user": "Jon",
    "command": "status"
  }
}
```
* Example response:
```json
{
  "success": true,
  "responseData": {
    "Status": "Complete",
    "Message": "Welcome home",
    "Temperature": "72"
  },
  "executionTime": 2.5
}
```


How It Works:

* Send a POST request with a piston ID and optional data
* App turns on a designated virtual switch (signals "piston running")
* App executes your piston with any data you sent
* Your piston does its work and stores results in a global variable
* Piston turns OFF the switch (signals "I'm done")
* App reads your global variable and returns it as JSON

Real-World Example:
Send from Tasker:
```json
{
  "pistonId": "abc123...",
  "data": {
    "pinCode": "1234",
    "user": "Jon"
  }
}
```

Your piston validates the PIN, checks some conditions, then sets a global variable like:
```
@@ResponseData = "Status:Success;Message:Welcome home Jon;DoorUnlocked:True;Temperature:72"
```
You receive back:
```json
{
  "success": true,
  "responseData": {
    "Status": "Success",
    "Message": "Welcome home Jon",
    "DoorUnlocked": "True",
    "Temperature": "72"
  },
  "executionTime": 2.5
}
```

**Setup Instructions:**

1. **Create a Virtual Switch** (if you don't have one):
   - Devices → Add Device → Virtual → Virtual Switch
   - Name it something like "webCoRE Proxy Switch"

2. **Install the App Code**:
   - Apps Code → New App → Paste code (link below)
   - **Important**: Enable OAuth in the app settings
   - Save

3. **Create an App Instance**:
   - Apps → Add User App → webCoRE_Proxy_App
   - Select your virtual switch
   - Enter your webCoRE App ID (find in any piston execute URL)
   - Enter your webCoRE Access Token (also in execute URL)
   - Optionally set a default piston ID
   - Choose your response variable name (defaults to @@ResponseData)
   - Click Done

4. **Copy Your URLs**:
   - The app displays both local and cloud URLs - copy these for your external system
   - Includes the access token, Hub UID, and everything you need

5. **Configure Your Piston**:
   - Create a global string variable in webCoRE (or use @@ResponseData)
   - Your piston must turn OFF the proxy switch at the end
   - Format response data as: `key1:value1;key2:value2;key3:value3`
   - The app will automatically convert this to proper JSON

**Response Data Format:**
To get clean JSON responses, format your webCoRE variable like this:

FieldName:Value;AnotherField:Another Value;Status:OK
```
The app automatically parses this into:
json{
  "FieldName": "Value",
  "AnotherField": "Another Value",
  "Status": "OK"
}
```

**Use Cases:**
- Mobile apps (Tasker) that need confirmation and data back from commands
- External dashboards pulling real-time calculated data from Hubitat
- Home Assistant or Node-RED integrations needing two-way communication
- Any scenario where you need more than "command received" - you need actual processed results
- PIN code validation with custom responses
- Multi-step automation workflows with status updates

**Example POST Request:**
```
URL: http://YOUR_HUB_IP/apps/api/YOUR_APP_ID/triggerPiston?access_token=YOUR_TOKEN
Method: POST
Body:
{
  "pistonId": "your-piston-id-here",
  "data": {
    "temperature": "25",
    "user": "John",
    "command": "status"
  }
}
