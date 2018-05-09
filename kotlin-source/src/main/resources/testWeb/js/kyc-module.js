"use strict";

//App module for issuing a TermDeposit
const app = angular.module('KYCTDAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('KYCTDAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    // We identify the node.
    const apiBaseURL = "/api/example/";
    document.getElementById("loading").style.display = "none"
    //populate the current offer options
    //var offers = [];
    var clients = []
    //getOffers();
    //getClients();

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

        demoApp.kyc = () => {
            window.location.href = "kyc_info.html"
        }

    //Load in available term deposit offers
//    function getOffers() {
//        $http.get("/api/term_deposits/offers").then(function (response) {
//            response.data.offers.forEach(function (element) {
//                offers.push(element);
//            });
//            loadOffers();
//        });
//    }

//    //Display the term deposit offers to the user
//    function loadOffers() {
//        var offers_select = document.getElementById("offers_select")
//            for (var i = 0; i < offers.length; i++) {
//                var option = document.createElement("option");
//                option.value = (offers[i]);
//                option.innerHTML = "Issuing Institute: " + String(offers[i].issuingInstitute) + "\nValid Till: "+
//                        String(offers[i].validTill) + "\nDuration: "+ String(offers[i].duration) + "\nInterest: "+
//                        String(offers[i].interest);
//                offers_select.appendChild(option);
//            }
//    }

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
        //TODO change this from confirm issue to update kyc data

    }

    //TODO Create edit KYC data function for editing a particular KYC dataset.

    demoApp.cancel = () => {
        alert("Cancelled");
        window.location.href = "homepage.html";
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

