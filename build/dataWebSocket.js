/*******************************************************
* The following section defines WebSocket functions for
* the data.html page
* Author: Paul Hemsworth    Email: hemsworp@my.erau.edu
*******************************************************/

const IDS = ["accX","accY","accZ","gyrX","gyrY","gyrZ","temp"];
var attUps = 0; // Attitude updates per second
var attUpsTimer = setInterval(repAttRate, 1000); // Report update rate every second

// Get the socket address and create the websocket. Use arraybuffer for binary data
const ATTITUDE_SOCKET_URL = window.location.hostname + ':' + window.location.port + '/speluncr/attitude';
const ATTITUDE_SOCKET = new WebSocket('ws://' + ATTITUDE_SOCKET_URL);
ATTITUDE_SOCKET.binaryType = 'arraybuffer';

// Event handler for the websocket opening
ATTITUDE_SOCKET.onopen = function(e) {
    document.getElementById('data').innerHTML+='Connection Established. Awaiting Data.<br>';
};

// Event handler for the websocket receiving data
ATTITUDE_SOCKET.onmessage = function(event) {
    // Server data is in little endian as this is most common.
    // There will be trouble if a system using big endian data tries to read this
    const buffer = new Float32Array(event.data);

    // Go through array of received data and set the corresponding HTML element
    var value, idx = 0;
    for (value of buffer){
        var id = IDS[idx];

        // Set table element values
        var element = document.getElementById(id);
        if (element != null){
            element.innerHTML = value.toFixed(2);
        }
        idx++;
    }
    attUps++; // This counts as an attitude update. Increment the counter
};

// Event handler for the websocket closing
ATTITUDE_SOCKET.onclose = function(event) {
    if (event.wasClean) {
        document.getElementById('data').innerHTML+=`Connection closed cleanly, code=${event.code} reason=${event.reason}<br>`;
    } else {
        // e.g. server process killed or network down
        // event.code is usually 1006 in this case
        alert('[close] Connection died');
    }
    // Set update rate to 0 Hz and report it, then stop reporting every second
    attUps=0;
    repAttRate();
    clearInterval(attUpsTimer);
};

// Event handler for websocket error
ATTITUDE_SOCKET.onerror = function(error) {
    alert(`[error] ${error.message}`);
};

function repAttRate(){
    document.getElementById("attUpdateRate").innerHTML=attUps;
    attUps=0;
}
