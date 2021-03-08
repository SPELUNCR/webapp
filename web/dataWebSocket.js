/*******************************************************
* The following section defines WebSocket functions for
* the data.html page
* Author: Paul Hemsworth    Email: hemsworp@my.erau.edu
*******************************************************/

const IDS = ["accX","accY","accZ","gyrX","gyrY","gyrZ","temp"];

// Get the socket address and create the websocket. Use arraybuffer for binary data
const SOCKET_URL = window.location.hostname + ':' + window.location.port + '/attitude';
const SOCKET = new WebSocket('ws://' + SOCKET_URL);
SOCKET.binaryType = 'arraybuffer';

// Event handler for the websocket opening
SOCKET.onopen = function(e) {
    document.getElementById('data').innerHTML+='Connection Established. Awaiting Data.<br>';
};

// Event handler for the websocket receiving data
SOCKET.onmessage = function(event) {  
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
            element.innerHTML = value.toPrecision(4);
        }
        idx++;
    }
};

// Event handler for the websocket closing
SOCKET.onclose = function(event) {
    if (event.wasClean) {
        document.getElementById('data').innerHTML+=`Connection closed cleanly, code=${event.code} reason=${event.reason}<br>`;
    } else {
        // e.g. server process killed or network down
        // event.code is usually 1006 in this case
        alert('[close] Connection died');
    }
};

// Event handler for websocket error
SOCKET.onerror = function(error) {
    alert(`[error] ${error.message}`);
};
