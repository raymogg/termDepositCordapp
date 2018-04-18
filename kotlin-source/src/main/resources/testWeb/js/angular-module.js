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
    var knownClients = [];
    //Get nodes name and display in browser
    displayNodeName();

    //Get cash held by this node
    $http.get("/api/example/cash").then(function (response) {
            //Display the total number of deposits up the top
            document.getElementById("cash").innerHTML = "Cash: "+ response.data.cash
    });

    // First we pull the TD's from the api -> these are displayed on the home screen
    $http.get("/api/term_deposits/deposits").then(function (response) {
            //Display the total number of deposits up the top
            document.getElementById("term_deposits").innerHTML = "Total Deposits: "+ response.data.states.length
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

    //TODO: Get known client data and display it on the homepage.
    $http.get("/api/term_deposits/kyc").then(function (response) {
                response.data.kyc.forEach(function (element) {
                    knownClients.push("First name: " + String(element.firstName) + ", Last name: " + String(element.lastName) + ", Account Num: " +
                    String(element.accountNum) + ", Unique Identifier: " + String(element.uniqueIdentifier.id));
                    loaded(knownClients, "knownClients","Known Clients");});
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

    demoApp.rolloverTD = () => {
        window.location.href = "rollover_td.html"
    }

    demoApp.home = () => {
            window.location.href = "index.html"
        }

    function displayNodeName() {
        //Make the API Call then change the title
        var url = "/api/example/me"
        $http.get(url).then(function (response) {
            var title = document.getElementById("title").innerHTML = "TD Cordapp: " + extractOrganisationName(response.data.me);
            var name_title = document.getElementById("node_title").innerHTML = "Welcome, " + extractOrganisationName(response.data.me);
            var img = document.createElement('img');
            if (extractOrganisationName(response.data.me) == "AMM") {
                img.src = 'img/' + 'amm_logo.png';
                document.getElementById('img_header').appendChild(img)
            } else if (extractOrganisationName(response.data.me) == "BankA" || name_title == "BankB") {
                img.src = 'img/' + 'commbank_logo.png';
                document.getElementById('img_header').appendChild(img)
            }
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