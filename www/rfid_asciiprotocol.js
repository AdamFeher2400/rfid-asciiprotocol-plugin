var exec = require('cordova/exec');

var PLUGIN_NAME = 'RFIDAsciiProtocol';

var RFIDAsciiProtocol = {
    isConnected: function(cb) {
        exec(cb, null, PLUGIN_NAME, 'isConnected', []);
    },
    connect: function(cb) {
        exec(cb, null, PLUGIN_NAME, 'connect', []);
    },
    scan: function(cb) {
        exec(cb, null, PLUGIN_NAME, 'scan', []);
    }
};
module.exports = RFIDAsciiProtocol;