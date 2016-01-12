var packageVersion = require('./../package.json').version;
console.log("packageVersion :: " + packageVersion);

var loopback = require('loopback');
var boot = require('loopback-boot');

var bodyParser = require('body-parser');

var request = require('request');

var app = module.exports = loopback();

app.use(bodyParser.json());

try {
        var vcap = JSON.parse(process.env.VCAP_SERVICES);
		var pushSecret = vcap.imfpush[0].credentials.appSecret;
		var appId = vcap.AdvancedMobileAccess[0].credentials.clientId;
	}catch (e) {
        console.error("Error encountered while obtaining Bluemix service credentials." +
            " Make certain that the Mobile Client Access and imfPush service are bound to this application." +
            " Error: " + e);
    }

// ------------ Protecting mobile backend with Mobile Client Access start -----------------

// Load passport (http://passportjs.org)
var passport = require('passport');

// Get the MCA passport strategy to use
var MCABackendStrategy = require('bms-mca-token-validation-strategy').MCABackendStrategy;

// Tell passport to use the MCA strategy
passport.use(new MCABackendStrategy())

// Tell application to use passport
app.use(passport.initialize());

// Protect DELETE endpoint so it can only be accessed by HelloTodo mobile samples
app.delete('/api/Items/:id', passport.authenticate('mca-backend-strategy', {session: false}));

// Protect /protected endpoint which is used in Getting Started with Bluemix Mobile Services tutorials
app.get('/protected', passport.authenticate('mca-backend-strategy', {session: false}), function(req, res){
	res.send("Hello, this is a protected resource of the mobile backend application!");
});

// Be sure to protect this from malicious users who want to send tons of push notifications
app.post('/notifyAllDevices', passport.authenticate('mca-backend-strategy', {session: false}), function(req, res){
	
	var jsonObject = 
	{
		"message": {
			"alert": "The following task has been completed: " + req.body.text
			}
		};
	
	request({
		url: "https://mobile.ng.bluemix.net/imfpush/v1/apps/" + appId + "/messages",
		method: "POST",
		json: true,
		body: jsonObject,
		headers: {
			'appSecret':pushSecret
		}
	}, function (error, response, body){
		if(!error && response.statusCode == 202){
			console.log(response.statusCode, "Notified all devices successfully: " + body);
			res.status(response.statusCode).send({result: "Sent notification to all registered devices.", response: body});
		}else if(error){
			console.log("Error from Push Service: " + error);
			res.status(response.statusCode).send({reason: "An error occurred while sending the Push notification.", error: error});
		}else{
			console.log("An unknown problem occurred, printing response");
			console.log(response);
			res.status(response.statusCode).send({reason: "A problem occurred while sending the Push notification.", message: body});
		}
		
	}); 

});

// ------------ Protecting backend APIs with Mobile Client Access end -----------------

app.start = function () {
	// start the web server
	return app.listen(function () {
		app.emit('started');
		var baseUrl = app.get('url').replace(/\/$/, '');
		console.log('Web server listening at: %s', baseUrl);
		var componentExplorer = app.get('loopback-component-explorer');
		if (componentExplorer) {
			console.log('Browse your REST API at %s%s', baseUrl, componentExplorer.mountPath);
		}
	});
};

// Bootstrap the application, configure models, datasources and middleware.
// Sub-apps like REST API are mounted via boot scripts.
boot(app, __dirname, function (err) {
	if (err) throw err;
	if (require.main === module)
		app.start();
});

