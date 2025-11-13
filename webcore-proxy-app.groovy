definition(
    name: "webCoRE_Proxy_App",
    namespace: "custom",
    author: "Custom",
    description: "Execute webCoRE pistons via HTTP and get data back in response",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    section("Select Switch") {
        input "testSwitch", "capability.switch", title: "Test Switch", required: true
        paragraph "Create a Virtual Switch device first if you haven't already (Devices → Add Device → Virtual → Virtual Switch)"
    }
    section("webCoRE Settings") {
        input "webCoreAppId", "text", title: "webCoRE App ID", required: false
        paragraph "Find this in the webCoRE piston execute URL (the number after /apps/api/)"
        input "webCoreAccessToken", "text", title: "webCoRE Access Token", required: false
        paragraph "Find this in any webCoRE piston's execute URL"
        input "webCorePiston", "text", title: "Default Piston ID (optional)", required: false
        paragraph "You can also send the piston ID in the POST body instead"
    }
    section("Response Options") {
        input "responseVariable", "text", title: "Response Variable Name", defaultValue: "@@ResponseData", required: false
        paragraph "Enter the webCoRE global variable to return in the response (e.g. @@ResponseData, @@MyVariable). To create a new global variable in webCoRE: Open any piston, click Edit, scroll to bottom and expand Available variables and comparisons, click the + next to global variable, name it (without @@) and set type to String, then use it in your piston as @@VariableName"
        input "includePistonId", "bool", title: "Include Piston ID in response", defaultValue: false, required: false
        paragraph "Enable this for testing/debugging. Disable for cleaner responses in production."
    }
    section("App Information") {
        if (state.accessToken) {
            def hubIP = location.hubs[0].getDataValue("localIP")
            paragraph "<b>App Access Token:</b><br>${state.accessToken}"
            paragraph "<b>App ID:</b><br>${app.id}"
            paragraph "<b>Hub UID:</b><br>${getHubUID()}"
            paragraph "<b>Hub IP:</b><br>${hubIP}"
            paragraph "<hr>"
            paragraph "<b>LOCAL URL (when on same network):</b><br>http://${hubIP}/apps/api/${app.id}/triggerPiston?access_token=${state.accessToken}"
            paragraph "<hr>"
            paragraph "<b>CLOUD URL (when remote):</b><br>https://cloud.hubitat.com/api/${getHubUID()}/apps/${app.id}/triggerPiston?access_token=${state.accessToken}"
        } else {
            paragraph "Access token will be generated when you save this app"
        }
    }
}

mappings {
    path("/turnOn") {
        action: [
            POST: "handleTurnOn"
        ]
    }
    path("/triggerPiston") {
        action: [
            POST: "handleTriggerPiston"
        ]
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    initialize()
}

def initialize() {
    if (!state.accessToken) {
        createAccessToken()
    }
    
    def hubUID = getHubUID()
    def hubIP = location.hubs[0].getDataValue("localIP")
    
    log.info "Access Token: ${state.accessToken}"
    log.info "App ID: ${app.id}"
    log.info "Hub UID: ${hubUID}"
    log.info "Hub IP: ${hubIP}"
    log.info ""
    log.info "LOCAL URL:"
    log.info "http://${hubIP}/apps/api/${app.id}/triggerPiston?access_token=${state.accessToken}"
    log.info ""
    log.info "CLOUD URL:"
    log.info "https://cloud.hubitat.com/api/${hubUID}/apps/${app.id}/triggerPiston?access_token=${state.accessToken}"
}

def handleTurnOn() {
    log.debug "handleTurnOn called"
    testSwitch.on()
    
    return [
        success: true,
        message: "Switch turned on",
        timestamp: now()
    ]
}

def handleTriggerPiston() {
    log.debug "handleTriggerPiston called"
    log.debug "Request body: ${request.JSON}"
    
    testSwitch.on()
    log.debug "Switch turned on"
    
    def pistonId = request.JSON?.pistonId ?: webCorePiston
    
    if (!pistonId || !webCoreAppId || !webCoreAccessToken) {
        return [
            success: false,
            message: "Switch turned on but webCoRE settings not fully configured or pistonId not provided"
        ]
    }
    
    try {
        def queryParams = [access_token: webCoreAccessToken]
        
        if (request.JSON?.data) {
            request.JSON.data.each { key, value ->
                queryParams[key] = value
            }
            log.debug "Added data parameters: ${request.JSON.data}"
        }
        
        def params = [
            uri: "http://${location.hubs[0].getDataValue("localIP")}:8080",
            path: "/apps/api/${webCoreAppId}/execute/:${pistonId}:",
            query: queryParams,
            timeout: 10
        ]
        
        log.debug "Calling webCoRE with params: ${queryParams.findAll { k, v -> k != 'access_token' }}"
        
        httpGet(params) { resp ->
            log.debug "Piston triggered with status ${resp.status}"
        }
        
        pauseExecution(50)
        
        testSwitch.refresh()
        pauseExecution(50)
        
        def switchState = testSwitch.latestValue("switch")
        log.debug "Initial switch state after trigger: ${switchState}"
        
        if (switchState != "on") {
            log.warn "Switch is not on after piston trigger! Current state: ${switchState}"
        }
        
        def maxWait = 30
        def elapsed = 0
        def loopCount = 0
        log.debug "Starting wait loop..."
        
        while (elapsed < maxWait) {
            testSwitch.refresh()
            pauseExecution(50)
            elapsed += 0.05
            loopCount++
            switchState = testSwitch.latestValue("switch")
            
            if (loopCount % 20 == 0) {
                log.debug "Switch state: ${switchState} at ${elapsed}s"
            }
            
            if (switchState == "off") {
                log.debug "Switch turned off detected!"
                break
            }
        }
        
        switchState = testSwitch.latestValue("switch")
        log.debug "Loop exited - Final switch state: ${switchState}, elapsed: ${elapsed}s"
        
        if (elapsed >= maxWait) {
            log.warn "Timeout waiting for switch to turn off"
            return [
                success: false,
                message: "Timeout waiting for piston to complete",
                timeout: true,
                elapsed: elapsed
            ]
        }
        
        log.debug "Switch turned off, waiting before retrieving response data..."
        pauseExecution(50)
        
        // Get the configured response variable (or default to @@ResponseData)
        def varName = responseVariable ?: "@@ResponseData"
        log.debug "Now fetching ${varName}..."
        def responseData = getWebCoreVariable(varName)
        log.debug "Retrieved responseData: ${responseData}"
        
        // Parse responseData if it's in key:value;key:value format
        def parsedData = null
        if (responseData && responseData instanceof String && responseData.contains(":")) {
            parsedData = parseResponseData(responseData)
            log.debug "Parsed responseData: ${parsedData}"
        }
        
        // Build response - conditionally include pistonId
        def response = [
            success: true,
            message: "Piston completed successfully",
            responseData: parsedData ?: responseData,
            executionTime: elapsed,
            timestamp: now()
        ]
        
        if (includePistonId) {
            response.pistonId = pistonId
        }
        
        return response
        
    } catch (e) {
        log.error "Error triggering piston: ${e}"
        return [
            success: false,
            message: "Error: ${e.message}"
        ]
    }
}

def getWebCoreVariable(variableName) {
    try {
        def params = [
            uri: "http://${location.hubs[0].getDataValue("localIP")}:8080",
            path: "/apps/api/${webCoreAppId}/global/${variableName}",
            query: [access_token: webCoreAccessToken],
            timeout: 5
        ]
        
        log.debug "Getting variable: ${variableName}"
        
        def result = null
        httpGet(params) { resp ->
            result = resp.data?.val
            log.debug "Variable ${variableName} = ${result}"
        }
        return result
        
    } catch (e) {
        log.error "Error getting variable: ${e}"
        return null
    }
}

def parseResponseData(String data) {
    try {
        def result = [:]
        
        // Split by semicolon to get key:value pairs
        def pairs = data.split(";")
        
        pairs.each { pair ->
            if (pair && pair.contains(":")) {
                // Split by first colon only (in case value contains colons)
                def parts = pair.split(":", 2)
                def key = parts[0].trim()
                def value = parts[1].trim()
                result[key] = value
            }
        }
        
        return result
        
    } catch (e) {
        log.error "Error parsing response data: ${e}"
        return data // Return original if parsing fails
    }
}
