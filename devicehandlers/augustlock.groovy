/**
 * This is a custom version of the Z-Wave Lock Device Type code. This code adds support for user codes with the use of a Smartapp.
 *
 * This Device Type was designed for the Kwikset Deadbolt so might not be compatible with other locks.
 *
 * Installation
 *
 * Create a new device type (https://graph.api.smartthings.com/ide/devices)
 *    Capabilities:
 *        Configuration
 *        Battery
 *        Polling
 *        Lock
 *    Custom Attribute
 *        lockStatus
 *        autoLock
 *        autoLockTime
 *    Custom Command
 *        getStatus
 */

preferences {
    //input("serverAddress", "text", title: "Server", description: "Your August-HTTP Server IP", required: "true", defaultValue: "test.wowwee.hk", displayDuringSetup: "true")
    ///input("serverPort", "number", title: "Port", description: "Your August-HTTP Server Port", required: "true", defaultValue: 80, displayDuringSetup: "true")
    input("augustUsername", "text", title: "August Username", description: "Your August Username for login, this is either email:you@blah.com or phone:+XXXXXXXXXX", required: "true", defaultValue: "", displayDuringSetup: "true")
    input("augustPassword", "text", title: "August Password", description: "Your August Password for login", required: "true", defaultValue: "", displayDuringSetup: "true")

    input("lockId", "text", title: "Lock Identifier", description: "Your August Lock Identifier", required: "true", defaultValue: "000", displayDuringSetup: "true")
    input("apiKey", "text", title: "API Key", description: "Your August Connect API Key", required: "true", defaultValue: "14445b6a2dba", displayDuringSetup: "true")
    input("autoLockSecs", "number", title: "Auto Lock Secs", description: "Seconds that your lock is set to auto-lock (0 for disabled)", required: "true", defaultValue: "", displayDuringSetup: "true")
    input("apiBaseUrl", "text", title: "API Base URL", description: "Where to send API requests to (only change this for testing)", required: "true", defaultValue: "https://api-production.august.com", displayDuringSetup: "true")
}

metadata {
    // Automatically generated. Make future change here.
    definition (name: "August Lock", namespace: "hongkongkiwi", author: "andy@savage.hk") {
        //capability "Configuration"
        capability "Lock"
        capability "Polling"
        capability "Refresh"
        //capability "Battery"

        //attribute "lockStatus", "enum", ["locking", "locked", "unlocking", "unlocked", "unknown"]
        //attribute "autoLock", "enum", ["true", "false"]
        //attribute "secondsUntilAutoLock", "number"
        //attribute "autoLockTime", "number"
        //attribute "remainingAutoLockTime", "number"

        //command "getStatus", ["boolean"]
        //command "getRelockTime"

        //command "unlockAndDisableAutoLock"
        //command "unlockAndEnableAutoLock" ["number", "relockTime"]
    }

    // simulator {
    //     //reply "cached": "{'remaining':'3600', 'lock':'locked', 'everlocktime':'3600'}"
    //
    //     reply "reamining":"3600","lock":"locked","everlocktime":"3600"
    // }

    tiles {
        standardTile("toggle", "device.lock", width: 2, height: 2) {
                state "unknown", label:"unknown", action:"lock.lock", icon:"st.locks.lock.unknown", backgroundColor:"#ffffff", nextState:"locking"
                state "locked", label:'locked', action:"lock.unlock", icon:"st.locks.lock.locked", backgroundColor:"#79b821", nextState:"unlocking"
                state "unlocked", label:'unlocked', action:"lock.lock", icon:"st.locks.lock.unlocked", backgroundColor:"#ffffff", nextState:"locking"
                state "locking", label:'locking', icon:"st.locks.lock.locked", backgroundColor:"#79b821"
                state "unlocking", label:'unlocking', icon:"st.locks.lock.unlocked", backgroundColor:"#ffffff"
        }
        standardTile("lock", "device.lock", inactiveLabel: false, decoration: "flat") {
                state "default", label:'lock', action:"lock.lock", icon:"st.locks.lock.locked", nextState:"locking"
        }
        standardTile("unlock", "device.lock", inactiveLabel: false, decoration: "flat") {
                state "default", label:'unlock', action:"lock.unlock", icon:"st.locks.lock.unlocked", nextState:"unlocking"
        }
        // valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
        //         state "battery", label:'${currentValue}% battery', action:"batteryupdate", unit:""
        // }
        // valueTile("usercode", "device.usercode", inactiveLabel: false, decoration: "flat") {
        //         state "usercode", label:'${currentValue}', unit:""
        // }
        standardTile("refresh", "device.lock", inactiveLabel: false, decoration: "flat") {
                state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        // standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
        //         state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        // }
        main "toggle"
        details(["toggle", "lock", "unlock", "refresh"])
    }
}

def userAgent = "August/4.4.42 (iPhone; iOS 9.0.2; Scale/2.00)";

def getAccessToken(username, password, installId) {
    def postParams = [
        uri: $apiBaseUrl,
        path: "/session",
        headers: [
            "x-kease-api-key": "$apiKey",
            "Accept-Encoding": "gzip, deflate",
            "Proxy-Connection": "keep-alive",
            "Connection": "keep-alive",
            "User-Agent": "$userAgent",
            "Accept-Language": "en-US;q=1",
            "accept-version": "0.0.1"
        ],
        body: [
            "identifier": $username,
            "installId": $installId || "000",
			"password": $password
        ]
    ]

    try {
        httpPostJson(postParams) { response ->
            if (response.status < 299) {
                def accessToken = reasponse.headers["x-august-access-token"]
                log.trace "Got Access Token: $accessToken"
                state.apiAccessToken = accessToken
            } else {
                log.trace "Got a strange response from server: $response"
            }
        }
    } catch (e) {
        log.trace "Failed to get Access Token $e"
    }
}

def lock() {
    log.debug "Executing 'lock'"
    api('lock_door')
}

def unlock() {
    log.debug "Executing 'unlock'"
    api('unlock_door')
}

def poll() {
    log.debug "Executing 'poll'"
    api('cached_status')
}

def refresh() {
    log.debug "Executing 'refresh'"
    api('real_status')
}

def configure() {
    log.debug "Executing 'configure'"
}

def setToLocked() {
	sendEvent(name: "lock", value: "locked")
	log.trace "Changed back to 'locked' since $autoLockSecs seconds have passed."
	//sendNotificationEvent("Front Door Lock was automatically re-locked after 30 seconds.")
}

private api(String command) {
    if (!state.apiAccessToken) {
        getAccessToken($augustUsername, $augustPassword, "000")
    }

    return

    def path = "";
    if (command == 'real_status') {
        path = "/remoteoperate/$lockId/status"
    } else if (command == 'cached_status') {
        path = "/locks/$lockId"
    } else if (command == 'lock_door') {
        sendEvent(name: 'lock', value: "locking")
        path = "/remoteoperate/$lockId/lock"
    } else if (command == 'unlock_door') {
        sendEvent(name: 'lock', value: "unlocking")
        path = "/remoteoperate/$lockId/unlock"
    } else {
        log.debug "Invalid Command $command"
    }

    def connectAPIHeaders = [
        uri: $apiBaseUrl,
        path: path,
        headers: [
        "x-kease-api-key": "$apiKey",
        "x-august-access-token": "$apiAccessToken",
        "Accept-Encoding": "gzip, deflate",
        "Proxy-Connection": "keep-alive",
        "Connection": "keep-alive",
        "User-Agent": "$userAgent",
        "Accept-Language": "en-US;q=1",
        "accept-version": "0.0.1"
        ]
    ]

    try {
        httpPutJson(connectAPIHeaders) { response ->
             parse(response);
         }
    } catch (e) {
        log.error "Something went wrong while executing command: $e"
        if (command == 'unlock') {
            log.trace "Setting state to unlocked anyway" //Again, I set the lock to 'unlocked' even though it failed because the lock seems to always still do what I requested, even if ST times out.
            sendEvent(name: "lock", value: "unlocked")
            runIn($autoLockSecs, setToLocked)
        } else {
            // Assuming door is locked due to auto-lock
            sendEvent(name: "lock", value: "locked")
        }
    }
}

def parse(response) {
    if (response.status != 200 ) {
        log.trace "Failed test"
        log.trace "There was an error executing $command"
        //sendNotificationEvent("There was an error unlocking Front Door Lock.")
    } else if (response.status == 200) {
       def answer = response.data
       def realAnswer = answer.status
       log.trace "${answer.status}"
       if (realAnswer == "kAugLockState_Unlocked") {
           sendEvent(name: "lock", value: "unlocked")
           //sendNotificationEvent("I have confirmed that Front Door Lock was unlocked.")
           // If we have just executed the command to unlock the door, set the door autolock
           if ($autoLockSecs > 0 && command == 'unlock_door') {
               runIn($autoLockSecs, setToLocked)
           }
        } else if (realAnswer == "kAugLockState_Locked") {
            sendEvent(name: "lock", value: "locked")
        } else {
            log.trace "There was an error unlocking the Front Door Lock."
            //sendNotificationEvent("There was an error unlocking Front Door Lock.")
        }
    }
}
