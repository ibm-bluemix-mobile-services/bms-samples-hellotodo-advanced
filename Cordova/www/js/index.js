/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

var app =  {

  // Bluemix credentials
  route: "<APPLICATION_ROUTE>",
  guid: "<APPLICATION_GUID>",

  // API route for Items model
  apiRoute: "/api/Items",

  // Initialize BMSClient
  initialize: function() {
    this.bindEvents();
  },

  // Bind Event Listeners
  //
  // Bind any events that are required on startup. Common events are:
  // 'load', 'deviceready', 'offline', and 'online'.
  bindEvents: function() {
    document.addEventListener('deviceready', this.onDeviceReady, false);
  },

  // deviceready Event Handler
  //
  // The scope of 'this' is the event. In order to use the 'route' and 'guid'
  // variables, we must explicitly call 'app.route' and 'app.guid'
  onDeviceReady: function() {
    BMSClient.initialize(app.route, app.guid);
    MFPAuthorizationManager.obtainAuthorizationHeader(app.authSuccess, app.authFailure);
  },

  // Make a call to our API to get all Items.
  // Update the table with the items
  getItems: function() {
    api.getItems(app.apiRoute, view.refreshTable, app.failure);
  },

  // Make a call to our API to add a new item
  // Update the table with the new items
  addItem: function() {
    api.addItem(app.apiRoute, app.getItems, app.failure);
  },

  // Make a call to our API to update a specific item
  // Update the table with the items
  updateItem: function(id) {
    api.setItem(app.apiRoute, id, view.updateItem(id, false), app.failure);
    api.notifyAllDevices(app.route, view.returnTextContent(id), view.returnCheckContent(id), app.notificationSuccess, app.failure);
  },

  // Enable input text and change edit to save button
  editItem: function(id) {
    view.changeToSave(id);
    view.updateItem(id, true);
  },

  // Make a call to our API to update a specific item
  // Disable input text and change save to edit button
  saveItem: function(id) {
    view.changeToEdit(id);
    view.updateItem(id, false);
    api.setItem(app.apiRoute, id, app.getItems, app.failure);
  },

  // Make a call to our API to delete a specific item
  deleteItem: function(id) {
    api.deleteItem(app.apiRoute, id, app.getItems, app.failure);
  },

  // Register for PUSH Notifications
  registerPush: function() {

    // Optional parameter, but must follow this format
    var settings = {
      ios: {
        alert: true,
        badge: true,
        sound: true
      }
    };

    MFPPush.registerDevice(settings, app.registeredPushSuccess, app.failure);
  },

  // Register for PUSH and get the items if MCA is successful
  authSuccess: function() {
    app.registerPush();
    app.apiRoute = app.route + app.apiRoute;
    app.getItems();
    var handleNotificationCallback = function(notification) {
      // notification is a JSON object
      alert(notification.message);
    }
    MFPPush.registerNotificationsCallback(handleNotificationCallback);
  },

  // If AUTH fails, alert user and prevent updates
  authFailure: function() {
    alert("Please configure authentication.");
    unauthorized();
  },

  // Registered PUSH success response
  registeredPushSuccess: function(res) {
    alert("App registered for PUSH notifications successfully.");
  },

  // Notification success response
  notificationSuccess: function(res) {
    console.log("Successfully notified all devices.");
  },

  // Standard success response
  success: function(res) {
    alert("Success: " + JSON.stringify(res));
  },

  // Standard failure response
  failure: function(res) {
    alert("Failure: " + JSON.stringify(res));
  }
};

app.initialize();
