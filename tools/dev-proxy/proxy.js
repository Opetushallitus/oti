var fs = require('fs');
var httpProxy = require('http-proxy');
var http = require('http');

http.createServer(function (req, res) {
    res.writeHead(301, { "Location": "https://" + req.headers['host'] + req.url });
    res.end();
}).listen(80);

httpProxy.createServer({
    ssl: {
        key: fs.readFileSync('key.pem', 'utf8'),
        cert: fs.readFileSync('cert.pem', 'utf8')
    },
    target: {
        host: 'localhost',
        port: 3000
    }
}).listen(443);
