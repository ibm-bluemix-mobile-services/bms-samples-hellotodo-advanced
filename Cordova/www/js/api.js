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

var api = {

  // Make a GET request to the API for all Items
  getItems: function(apiRoute, success, failure) {

    // Set up the GET request
    var request = new MFPRequest(apiRoute, MFPRequest.GET);

    // Send the request
    request.send(success, failure);
  },

  // Make a POST request to the API to add a new item
  addItem: function(apiRoute, success, failure) {

    // Set up the POST request and set headers
    var request = new MFPRequest(apiRoute, MFPRequest.POST);
    var headers = {
      "Content-Type": "application/json",
      "Accept": "application/json",
    };
    request.setHeaders(headers);

    // Item text
    var inputID = "todo-add-item";
    var text = document.getElementById(inputID).value;

    // Set the body of the request
    var body = {
      "text": text,
      "isDone": false
    };

    document.getElementById(inputID).value = "";

    // Send the request
    request.send(body, success, failure);
  },

  // Make a PUT request to the API to update the specific item with specified ID
  setItem: function(apiRoute, id, success, failure) {

    // /api/Items/:id
    var url = apiRoute + "/" + id;

    // Set up the PUT request and set the headers
    var request = new MFPRequest(url, MFPRequest.PUT);
    var headers = {
      "Content-Type": "application/json",
      "Accept": "application/json",
    };
    request.setHeaders(headers);

    // Item text
    var inputID = "todo-item-input-" + id;
    var text = document.getElementById(inputID).value;

    // Item isDone
    var checkboxID = "todo-item-checkbox-" + id;
    var checked = document.getElementById(checkboxID).checked;

    // Set the body of the request
    var body = {
      "text": text,
      "isDone": checked,
      "id": id
    };

    // Send the request with JSON body
    request.send(body, success, failure);
  },

  // Make a DELETE request to the API to delete the specific item with specified ID
  deleteItem: function(apiRoute, id, success, failure) {

    // /api/Items/:id
    var url = apiRoute + "/" + id;

    // Set up the GET request and set the headers
    var request = new MFPRequest(url, MFPRequest.DELETE);
    var headers = {
      "Content-Type": "application/json",
      "Accept": "application/json",
    };
    request.setHeaders(headers);

    // Send the request
    request.send(success, failure);
  },

  // Notify all devices
  notifyAllDevices: function(route, completedItem, isChecked, success, failure) {

    // Only push notification if the device is checked
    if (isChecked) {

      var url = route + "/notifyAllDevices";

      // Set up the POST request and set the headers
      var request = new MFPRequest(url, MFPRequest.POST);
      var headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
      };
      request.setHeaders(headers);

      // Set the body of the request
      var body = {
        "text": completedItem,
      };

      // Send the request
      request.send(body, success, failure);
    }
  }
};
