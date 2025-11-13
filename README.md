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
 * Install this code in Apps Code
 * Enable OAuth for the app
 * Create an app instance and configure with your virtual switch and webCoRE details
 * Copy the URLs provided in the app settings

Your Piston Requirements:

Must turn OFF the designated virtual switch at the end (signals completion)
Store response data in a global string variable (default: @@ResponseData)
Format response as: Key1:Value1;Key2:Value2;Key3:Value3
