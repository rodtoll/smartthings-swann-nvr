/**
 *  Copyright 2015 Rod Toll
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  SWANN NVR Camera DeviceType
 *
 *  Author: rodtoll
 */
 metadata {
	definition (name: "SwannNVRCamera", namespace: "rodtoll", author: "SmartThings") {
		capability "Actuator"
		capability "Image Capture"
        attribute "cameraIndex", "number"        
	}

	simulator {
		// TODO: define status and reply messages here
	}

	//TODO:encrypt these settings and make them required:true
	preferences {
            input "swannAddress", "text", title: "ISY Address", required: false, defaultValue: "10.0.1.80" 			// Address of the swann Nvr
            input "swannPort", "number", title: "Swann NVR Port", required: false, defaultValue: 85 				// Port to use for the HTTP interface
            input "swannUserName", "text", title: "User Name", required: false, defaultValue: "admin"				// Username to use with the NVR
			input "swannPassword", "text", title: "Password", required: false, defaultValue: "Alpha1Romero"			// Password to use with the NVR
	}

	tiles {
		standardTile("image", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: true) {
			state "default", label: "", action: "", icon: "st.camera.dropcam-centered", backgroundColor: "#FFFFFF"
		}

		carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }

		standardTile("take", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#FFFFFF", nextState:"taking"
			state "taking", label:'Taking', action: "", icon: "st.camera.dropcam", backgroundColor: "#53a7c0"
			state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#FFFFFF", nextState:"taking"
		}

		main "image"
		details(["cameraDetails", "take"])
	}
}

def setCameraIndex(index) {
	sendEvent(name: "cameraIndex", value: index)
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"

	def map = stringToMap(description)
	log.debug map

	def result = []

	if (map.bucket && map.key)
	{ //got a s3 pointer
		putImageInS3(map)
        parent.resetActiveSnapshot()
	}

	result
}

def putImageInS3(map) {

	def s3ObjectContent

	try {
		def imageBytes = getS3Object(map.bucket, map.key + ".jpg")

		if(imageBytes)
		{
			s3ObjectContent = imageBytes.getObjectContent()
			def bytes = new ByteArrayInputStream(s3ObjectContent.bytes)
			storeImage(getPictureName(), bytes)
		}
	}
	catch(Exception e) {
		log.error e
	}
	finally {
		//explicitly close the stream
		if (s3ObjectContent) { s3ObjectContent.close() }
	}
}

// handle commands
/*def take() {
	log.debug "Executing 'take'"
	//Snapshot uri depends on model number:
	//because 8 series uses user and 9 series uses usr -
	//try based on port since issuing a GET with usr to 8 series causes it throw 401 until you reauthorize using basic digest authentication

	def host = getHostAddress()
	def port = host.split(":")[1]
	def path = (port == "80") ? "/snapshot.cgi?user=${getUsername()}&pwd=${getPassword()}" : "/cgi-bin/CGIProxy.fcgi?usr=${getUsername()}&pwd=${getPassword()}&cmd=snapPicture2"


	def hubAction = new physicalgraph.device.HubAction(
		method: "GET",
		path: path,
		headers: [HOST:host]
	)
	hubAction.options = [outputMsgToS3:true]
	hubAction
}*/

//////////////////////
// WORKING METHODS
//

def take() {
	/*def requestPath = '/PSIA/Streaming/channels/'+device.currentValue('cameraIndex').toString()+'01/picture'
	log.debug "SWANNNVRCAMERA: Taking picture using..."+requestPath
    def request = getRequest(requestPath)
    log.debug "SWANNNVRCAMERA: Request: "+request
    sendHubCommand(request)*/
    parent.take(device.currentValue('cameraIndex'))
}

////////////////////
// HELPER METHODS
//

//helper methods
private getPictureName() {
	def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
    def pictureName = device.deviceNetworkId + "_$pictureUuid" + ".jpg"
    log.debug "SWANNNVRCAMERA: Writing picture to: "+pictureName
	return pictureName
}

private getAuthorization() {
    def userpassascii = settings.swannUserName + ":" + settings.swannPassword
    "Basic " + userpassascii.encodeAsBase64().toString()
}

private String makeNetworkId(ipaddr, port) { 
     String hexIp = ipaddr.tokenize('.').collect { 
     String.format('%02X', it.toInteger()) 
     }.join() 
     String hexPort = String.format('%04X', port) 
     return "${hexIp}:${hexPort}" 
}

def getRequest(path) {
	log.debug 'SWANNNVRCAMERA: Address='+settings.swannAddress+":"+settings.swannPort+" UserPass="+settings.swannUserName + ":"+settings.swannPassword
    def hubAction = new physicalgraph.device.HubAction(
        'method': 'GET',
        'path': path,
        'headers': [
            'HOST': settings.swannAddress+":"+settings.swannPort,
            'Authorization': getAuthorization()
        ], null)
	hubAction.options = [outputMsgToS3:true]
	hubAction        
}
