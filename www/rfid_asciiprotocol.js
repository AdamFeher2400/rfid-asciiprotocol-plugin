var exec = require('cordova/exec');

var PLUGIN_NAME = 'RFIDAsciiProtocol';

var RFIDAsciiProtocol = {
    init: function(cb) {
        exec(null, null, PLUGIN_NAME, 'init', []);
    },
    isConnected: function(cb) {
        exec(cb, null, PLUGIN_NAME, 'isConnected', []);
    },
    connect: function(cb) {
        exec(cb, null, PLUGIN_NAME, 'connect', []);
    },
    disconnect: function(cb) {
        exec(null, null, PLUGIN_NAME, 'disconnect', []);
    },
    scan: function(cb) {
        exec(null, null, PLUGIN_NAME, 'scan', []);
    }
};
module.exports = RFIDAsciiProtocol;