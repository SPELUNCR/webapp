/*******************************************************
* The following section defines WebSocket functions for
* the data.html page
* Author: Paul Hemsworth    Email: hemsworp@my.erau.edu
*******************************************************/

/**********************************************************
* NAVBALL RENDERING AND ANIMATION
* Three.js code for loading and animating the navball
**********************************************************/
import * as THREE from './node_modules/three/build/three.module.js';
import { GLTFLoader } from './node_modules/three/examples/jsm/loaders/GLTFLoader.js';

// Setup scene
const canvas = document.querySelector('#glCanvas'); // html canvas element to render on
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(10, canvas.width / canvas.height, 0.1, 20);
const light = new THREE.AmbientLight(0xffffff);
scene.add(light);

// Set up scene renderer using the canvas and transparent background
const renderer = new THREE.WebGLRenderer({canvas, alpha:true});
renderer.setSize(canvas.width, canvas.height);

// Load the navball
var navball; // use this to access navball later
const loader = new GLTFLoader();
loader.load('navball.gltf', function(gltf) {
	navball = gltf.scene;
    scene.add(gltf.scene);
}, undefined, function (error) {
    console.error(error);
});

camera.position.z = 12;

// Set up scene animation
const animate = function () {
    requestAnimationFrame(animate);
    renderer.render(scene, camera);
};

animate();

// Set navball orientation from roll, pitch and yaw values
function setNavballRPY(roll, pitch, yaw){
	if (typeof navball !== 'undefined'){
		navball.setRotationFromEuler(new THREE.Euler(-pitch, yaw, roll, 'XYZ'));
	}
}

/**********************************************************
* WEBSOCKET CONNECTION
* Connects to the java servlet to obtain data from SPELUNCR
**********************************************************/
var attUps = 0; // Attitude updates per second
var attUpsTimer = setInterval(repAttRate, 1000); // Report update rate every second

// Get the socket addresses and create the websockets. Use arraybuffer for binary data
const ATTITUDE_SOCKET_URL = window.location.hostname + ':' + window.location.port + '/speluncr/attitude';
const RADIATION_SOCKET_URL = window.location.hostname + ':' + window.location.port + '/speluncr/radiation';
const ATTITUDE_SOCKET = new WebSocket('ws://' + ATTITUDE_SOCKET_URL);
const RADIATION_SOCKET = new WebSocket('ws://' + RADIATION_SOCKET_URL);
ATTITUDE_SOCKET.binaryType = 'arraybuffer';
RADIATION_SOCKET.binaryType = 'arraybuffer';

// Event handler for the websocket opening
ATTITUDE_SOCKET.onopen = function(event) {
    websocketOpen(event, 'Attitude');
};

RADIATION_SOCKET.onopen = function(event) {
    websocketOpen(event, 'Radiation');
};

function websocketOpen(event, socketName){
    document.getElementById('data').innerHTML+=`${socketName} Websocket Connected. Awaiting Data.<br>`;
}

// Event handlers for the websockets receiving data
// Server data is in little endian as this is most common.
// There will be trouble if a system using big endian data tries to read this
ATTITUDE_SOCKET.onmessage = function(event) {
	// Interpret data
    const buffer = new Float64Array(event.data);
	const roll	= buffer[0];
	const pitch = buffer[1];
	const yaw 	= buffer[2];
	const temp	= buffer[3];

	// Change navball orientation
	setNavballRPY(roll, pitch, yaw);

	// Set values of HTML table elements
	document.getElementById("roll").innerHTML = (roll * 180 / Math.PI).toFixed(2) + '&deg';
	document.getElementById("pitch").innerHTML = (pitch * 180 / Math.PI).toFixed(2) + '&deg';
	document.getElementById("yaw").innerHTML = (yaw * 180 / Math.PI).toFixed(2) + '&deg';
	document.getElementById("temp").innerHTML = temp.toFixed(2) + "&degC";

    attUps++; // This counts as an attitude update. Increment the counter
};

RADIATION_SOCKET.onmessage = function(event) {
    const buffer = new Int32Array(event.data);
    document.getElementById('cps').innerHTML = buffer[0] + " cps";
};

// Event handler for the websocket closing
ATTITUDE_SOCKET.onclose = function(event) {
    websocketClose(event, 'Attitude');

    // Set update rate to 0 Hz and report it, then stop reporting every second
    attUps=0;
    repAttRate();
    clearInterval(attUpsTimer);
};

RADIATION_SOCKET.onclose = function(event) {
    websocketClose(event, 'Radiation');
};

function websocketClose(event, socketName){
    if (event.wasClean) {
        document.getElementById('data').innerHTML+=`${socketName} websocket closed cleanly, code=${event.code} reason=${event.reason}<br>`;
    } else {
        alert('[close] Connection died');
    }
}

// Event handler for websocket error
ATTITUDE_SOCKET.onerror = function(error){
    websocketError(error, 'Attitude');
};
RADIATION_SOCKET.onerror = function(error){
    websocketError(error, 'Radiation');
};

function websocketError(error, websocketName){
    alert(`[ERROR] ${websocketName} websocket ${error.message}`);
}

// Report the attitude update rate
function repAttRate(){
    document.getElementById("attUpdateRate").innerHTML=attUps + " Hz";
    attUps=0;
}
