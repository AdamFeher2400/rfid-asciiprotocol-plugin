var exec = require('cordova/exec');

var PLUGIN_NAME = 'RFID-AsciiProtocol';

var RFIDAsciiProtocol = {
    echo: funciton(phrase, cb) {
        exec(cb, null, PLUGIN_NAME, 'echo', [phrase]);
    },
    getDate: function(cb) {
        exec(cb, null, PLUGIN_NAME, 'getDate', []);
    }
};
module.exports = RFIDAsciiProtocol;