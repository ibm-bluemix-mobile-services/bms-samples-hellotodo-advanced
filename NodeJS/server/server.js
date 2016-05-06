var packageVersion = require('./../package.json').version;
console.log("packageVersion :: " + packageVersion);

var loopback = require('loopback');
var boot = require('loopback-boot');

// Library needed to parse through VCAP services
var bodyParser = require('body-parser');

var request = require('request');

var app = module.exports = loopback();

// Must indicate the use of the bodyParser library in the app server
app.use(bodyParser.json());

// Grab the push secret and appId directly from VCAP services on Bluemix! Note that Mobile Client Access stores the appId as the clientId value, they are one and the same. 
try {
        var vcap = JSON.parse(process.env.VCAP_SERVICES);
		var pushSecret = vcap.imfpush[0].credentials.appSecret;
		var appId = vcap.AdvancedMobileAccess[0].credentials.clientId;
		var url = vcap.AdvancedMobileAccess[0].credentials.serverUrl;
		url = url.split('.');
	
	}catch (e) {
        console.error("Error encountered while obtaining Bluemix service credentials." +
            " Make certain that the Mobile Client Access and IBM Push Notifications service are bound to this application." +
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

// Protected DELETE endpoint that uses our MCA backend strategy as the authentication mechanism.  This ensures that only authenticated Bluemix Mobile Services client SDKs can access the endpoint.
app.delete('/api/Items/:id', passport.authenticate('mca-backend-strategy', {session: false}));

// Protected GET endpoint which is used in the authentication process for Bluemix Mobile Services tutorials.
app.get('/protected', passport.authenticate('mca-backend-strategy', {session: false}), function(req, res){
	res.send("Hello, this is a protected resource of the mobile backend application!");
});

// Protected POST endpoint which allows us to send push notifications. You should protect this endpoint to ensure malicious users do not send unwanted push notifications.
app.post('/notifyAllDevices', passport.authenticate('mca-backend-strategy', {session: false}), function(req, res){
	
	// Create JSON body to include the completed task in push notification.
	var jsonObject = 
	{
		"message": {
			"alert": "The following task has been completed: " + req.body.text
			}
		};
	
	// Formulate and send outbound REST request using the request.js library
	request({
		url: "https://mobile." + url[1] + ".bluemix.net/imfpush/v1/apps/" + appId + "/messages",
		method: "POST",
		json: true,
		body: jsonObject,
		headers: {
			'appSecret':pushSecret
		}
	}, function (error, response, body){
		if(!error && response.statusCode == 202){
			console.log(response.statusCode, "Notified all devices successfully: " + body);
			// on success, respond to mobile app appropriately
			res.status(response.statusCode).send({result: "Sent notification to all registered devices.", response: body});
		}else if(error){
			// If an error occurred log and send to mobile app
			console.log("Error from Push Service: " + error);
			res.status(response.statusCode).send({reason: "An error occurred while sending the push notification.", error: error});
		}else{
			// if no error but something else goes wrong, like no devices are registered, print response and send body to mobile app
			console.log("An unknown problem occurred, printing response");
			console.log(response);
			res.status(response.statusCode).send({reason: "A problem occurred while sending the push notification.", message: body});
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

