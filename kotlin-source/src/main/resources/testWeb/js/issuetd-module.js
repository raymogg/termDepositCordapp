"use strict";

//App module for issuing a TermDeposit
const app = angular.module('IssueTDAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('IssueTDAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    // We identify the node.
    const apiBaseURL = "/api/example/";
    document.getElementById("loading").style.display = "none"
    //populate the current offer options
    var offers = [];
    var clients = []
    getOffers();
    getClients();

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
                            window.location.href = "homepage.html"
                        }

    //Load in available term deposit offers
    function getOffers() {
        $http.get("/api/term_deposits/offers").then(function (response) {
            response.data.offers.forEach(function (element) {
                offers.push(element);
            });
            loadOffers();
        });
    }

    //Display the term deposit offers to the user
    function loadOffers() {
        var offers_select = document.getElementById("offers_select")
            for (var i = 0; i < offers.length; i++) {
                var option = document.createElement("option");
                option.value = (offers[i]);
                option.innerHTML = "Issuing Institute: " + String(offers[i].issuingInstitute) + "\nValid Till: "+
                        String(offers[i].validTill) + "\nDuration: "+ String(offers[i].duration) + "\nInterest: "+
                        String(offers[i].interest);
                offers_select.appendChild(option);
            }
    }

    //Get all the kyc data this user has
    function getClients() {
        $http.get("/api/term_deposits/kyc").then(function (response) {
            response.data.kyc.forEach(function (element) {
                clients.push(element);
            });
            loadClients();
        });
    }

    //Display this KYC data
    function loadClients() {
        var client_select = document.getElementById("client_select")
            for (var i = 0; i < clients.length; i++) {
                var option = document.createElement("option");
                option.value = clients[i];
                option.innerHTML = String(clients[i].firstName) + " "+
                        String(clients[i].lastName) + "\nAccount Num: "+ String(clients[i].accountNum) + "\nUniqueID: "+
                        String(clients[i].uniqueIdentifier.id);
                client_select.appendChild(option);
            }
    }

    //OnClick method for Issuing the term deposit
    demoApp.confirmIssue = () => {
        //Load in the required data
        var offer = document.getElementById("offers_select");
        var client = document.getElementById("client_select");
        var selectedOffer = offers[offer.selectedIndex];
        var selectedClient = clients[client.selectedIndex];
        //Parse options selected and pull the data
        var value = parseFloat(document.getElementById("depositAmount").value);
        var offering_institute = extractOrganisationName(selectedOffer.issuingInstitute);
        var interest_percent = selectedOffer.interest;
        var duration = selectedOffer.duration;
        var customer_fname = selectedClient.firstName;
        var customer_lname = selectedClient.lastName;
        var customer_anum = selectedClient.accountNum;
        var url = "/api/term_deposits/issue_td?td_value="+value+"&offering_institute="+offering_institute+"&interest_percent="+interest_percent+
        "&duration="+duration+"&customer_fname="+customer_fname+"&customer_lname="+customer_lname+"&customer_anum="+customer_anum;
                    //This is how you execute the post
        //Display a loading circle
        document.getElementById("loading").style.display = "block"
        $http.post(url).then(function (response) {
            document.getElementById("loading").style.display = "none"
            alert(String(response.data));
            window.location.href = "index.html";
        });
    }

        demoApp.cancel = () => {
            alert("Cancelled");
            window.location.href = "index.html";
        }

        //Helper function to extract the needed organisation name from a formatted Corda Name String
        //This organisatino name is needed to issue a new TD.
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
 });

