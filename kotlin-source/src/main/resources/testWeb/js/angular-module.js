"use strict";

//Main JS App for Term Deposits
const app = angular.module('demoAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('DemoAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    //Variables for TD app
    var activeTDs = [];
    var currentOffers = [];
    //Get nodes name and display in browser
    displayNodeName();

    // First we pull the TD's from the api -> these are displayed on the home screen
    $http.get("/api/term_deposits/deposits").then(function (response) {
            response.data.states.forEach(function (element) {
            activeTDs.push("From: " + String(element.from) + ", Amount: " + String(element.amount) + ", Ending: " +
            String(element.endDate) + ", Internal State: " + String(element.internalState));
            loaded(activeTDs, "currentDeposits", "Current Deposits");
            });
    });


    //Next we pull all offers this node has and display these
    $http.get("/api/term_deposits/offers").then(function (response) {
                response.data.offers.forEach(function (element) {
                currentOffers.push("From: " + String(element.issuingInstitute) + ", Interest: " + String(element.interest) + ", Duration: " +
                String(element.duration) + ", Valid Until: " + String(element.validTill));
                loaded(currentOffers, "currentOffers","Current Offers");});
    });


    //OnClick methods for each button -> used for loading new pages for TD functionality
    demoApp.issueTD = () => {
        window.location.href = "issue_td.html";
    }

    demoApp.activateTD = () => {
            window.location.href = "activate_td.html";
    }

    //Note this will fail if not called from a bank node.
    demoApp.redeemTD = () => {
        window.location.href = "redeem_td.html";
    }

    function displayNodeName() {
        //Make the API Call then change the title
        var url = "/api/example/me"
        $http.get(url).then(function (response) {
            var title = document.getElementById("title").innerHTML = "TD Cordapp: " + extractOrganisationName(response.data.me);
            var name_title = document.getElementById("node_title").innerHTML = "Welcome, " + extractOrganisationName(response.data.me);
        });
    }

    function extractOrganisationName(partyString) {
                var actualName = "";
                var seenEqual = 0;
                //Extract and return the party name by parsing a string
                for(var i = 0; i < partyString.length; i++) {
                    if (partyString.charAt(i) == '=') {
                        //First instance of seeing = is right after O, so from now till next L we append these to the name
                        if (!seenEqual) {
                            seenEqual = 1;
                        }
                    } else if (partyString.charAt(i) == ',') {//stop parsing the name
                        return actualName;
                    } else if (seenEqual) {
                        actualName += partyString.charAt(i);
                    }
                }
            }

    //Load the TDs into the UI
    function loaded(array, element_title, title) {
        var ul = document.createElement('ul');
        ul.setAttribute('id','proList');
        var t, tt;
        document.getElementById(element_title).innerHTML = "<h3>" + title+ "</h3>";
        document.getElementById(element_title).appendChild(ul);
        array.forEach(function (element, index, arr) {
            var li = document.createElement('li');
            li.setAttribute('class','item');
            ul.appendChild(li);
            t = document.createTextNode(element);
            li.innerHTML=li.innerHTML + element;
        });
    }

});