/*******************************************************
* The following section defines WebSocket functions for
* the data.html page
* Author: Paul Hemsworth    Email: hemsworp@my.erau.edu
*******************************************************/

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
ATTITUDE_SOCKET.onmessage = function(event) {
    // Server data is in little endian as this is most common.
    // There will be trouble if a system using big endian data tries to read this
    const IDS = ["roll","pitch","yaw","temp"];
    const buffer = new Float64Array(event.data);

    // Go through array of received data and set the corresponding HTML element
    var value, idx = 0;
    for (value of buffer){
        // Set table element values
        var element = document.getElementById(IDS[idx]);
        if (element != null){
            element.innerHTML = value.toFixed(2);
        }
        idx++;
    }
    attUps++; // This counts as an attitude update. Increment the counter
};

RADIATION_SOCKET.onmessage = function(event) {
    // Server data is in little endian as this is most common.
    // There will be trouble if a system using big endian data tries to read this
    const buffer = new Int32Array(event.data);
    document.getElementById('cps').innerHTML = buffer[0];
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
        // e.g. server process killed or network down
        // event.code is usually 1006 in this case
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
    document.getElementById("attUpdateRate").innerHTML=attUps;
    attUps=0;
}
