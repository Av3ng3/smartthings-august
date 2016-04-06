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
    input("serverAddress", "text", title: "Server", description: "Your August-HTTP Server IP", required: "true", defaultValue: "test.wowwee.hk", displayDuringSetup: "true")
    input("serverPort", "number", title: "Port", description: "Your August-HTTP Server Port", required: "true", defaultValue: 80, displayDuringSetup: "true")
}

metadata {
    // Automatically generated. Make future change here.
    definition (name: "August Lock", namespace: "hongkongkiwi", author: "andy@savage.hk") {
        //capability "Configuration"
        capability "Lock"
        capability "Polling"
        capability "Refresh"
        //capability "Battery"

        attribute "lockStatus", "enum", ["locking", "locked", "unlocking", "unlocked", "unknown"]
        attribute "autoLock", "enum", ["true", "false"]
        attribute "secondsUntilAutoLock", "number"
        attribute "autoLockTime", "number"
        //attribute "remainingAutoLockTime", "number"

        //command "getStatus", ["boolean"]
        //command "getRelockTime"

        //command "unlockAndDisableAutoLock"
        //command "unlockAndEnableAutoLock" ["number", "relockTime"]
    }

    simulator {
        //reply "cached": "{'remaining':'3600', 'lock':'locked', 'everlocktime':'3600'}"

        reply "reamining":"3600","lock":"locked","everlocktime":"3600"
    }

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
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
                state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }
        main "toggle"
        details(["toggle", "lock", "unlock", "configure", "refresh"])
    }
}

def parseEventData(Map results) {
    results.each { name, value ->
        if (name == "remaining") {
            sendEvent(name: 'secondsUntilAutoLock', value: value)
        } else if (name == "lock") {
            if (value == "locked") {
                sendEvent(name: 'lockStatus', value: "locked")
            } else if (value == "unlocked") {
                sendEvent(name: 'lockStatus', value: "unlocked")
            }
        } else if (name == "everlocktime") {
            sendEvent(name: 'autoLockTime', value: value)
        }
    }
}

def lock() {
    log.debug "Executing 'lock'"
    api('lock', null)
    sendEvent(name: 'lockStatus', value: "locking")
}

def unlock() {
    log.debug "Executing 'unlock'"
    api('unlock')
    sendEvent(name: 'lockStatus', value: "unlocking")
}

def poll() {
    log.debug "Executing 'poll'"
    results = parent.pollChildren()
    parseEventData(results)
}

def refresh() {
    log.debug "Executing 'refresh'"
    poll()
    //getStatus(false)
}

def configure() {
    log.debug "Executing 'configure'"
}

/** Custom Commands **/
def getStatus(Boolean cached = "true") {
    if (cached == "true") {
        api("cached")
    } else {
        api("status")
    }
}

// def getRelockTime() {
//     api("relocktime")
// }

private api(String command, Map params = null) {
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/august/control/${command}",
        headers: [
            HOST: getHostAddress()
        ],
        query: params
    )
}

def parse(description) {
    def msg = parseLanMessage(description)

    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
}

// gets the address of the hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

// gets the address of the device
private getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
