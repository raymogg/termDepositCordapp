"use strict";

// --------
// WARNING:
// --------

// THIS CODE IS ONLY MADE AVAILABLE FOR DEMONSTRATION PURPOSES AND IS NOT SECURE!
// DO NOT USE IN PRODUCTION!

// FOR SECURITY REASONS, USING A JAVASCRIPT WEB APP HOSTED VIA THE CORDA NODE IS
// NOT THE RECOMMENDED WAY TO INTERFACE WITH CORDA NODES! HOWEVER, FOR THIS
// PRE-ALPHA RELEASE IT'S A USEFUL WAY TO EXPERIMENT WITH THE PLATFORM AS IT ALLOWS
// YOU TO QUICKLY BUILD A UI FOR DEMONSTRATION PURPOSES.

// GOING FORWARD WE RECOMMEND IMPLEMENTING A STANDALONE WEB SERVER THAT AUTHORISES
// VIA THE NODE'S RPC INTERFACE. IN THE COMING WEEKS WE'LL WRITE A TUTORIAL ON
// HOW BEST TO DO THIS.



const app = angular.module('IssueTDAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('IssueTDAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    // We identify the node.
    const apiBaseURL = "/api/example/";

    //populate the current offer options
    var offers = [];
    var clients = []
    getOffers();
    getClients();

    function getOffers() {
                    $http.get("/api/term_deposits/offers").then(function (response) {
                        response.data.offers.forEach(function (element) {
                            offers.push("Issuing Institute: " + String(element.issuingInstitute) + "\nValid Till: "+
                            String(element.validTill) + "\nDuration: "+ String(element.duration) + "\nInterest: "+
                            String(element.interest));
                        });
                        loadOffers();
                });
    }

    function loadOffers() {
        var offers_select = document.getElementById("offers_select")
                    for (var i = 0; i < offers.length; i++) {
                        var option = document.createElement("option");
                        option.value = String(offers[i]);
                        option.innerHTML = String(offers[i]);
                        offers_select.appendChild(option);
                    }
    }

    function getClients() {
                        $http.get("/api/term_deposits/kyc").then(function (response) {
                            response.data.kyc.forEach(function (element) {
                                clients.push(String(element.firstName) + " "+
                                String(element.lastName) + "\nAccount Num: "+ String(element.accountNum) + "\nUniqueID: "+
                                String(element.uniqueIdentifier.id));
                            });
                            loadClients();
                    });
        }

        function loadClients() {
            var client_select = document.getElementById("client_select")
                        for (var i = 0; i < clients.length; i++) {
                            var option = document.createElement("option");
                            option.value = String(clients[i]);
                            option.innerHTML = String(clients[i]);
                            client_select.appendChild(option);
                        }
        }

    demoApp.confirmIssue = () => {
            //Load in the required data

            var value = 500;
            var offering_institute = "BankA";
            var interest_percent = 2.55;
            var duration = 6;
            var customer_fname = "Jane";
            var customer_lname = "Doe";
            var customer_anum = "9384";
            var url = "/api/term_deposits/issue_td?td_value="+value+"&offering_institute="+offering_institute+"&interest_percent="+interest_percent+
            "&duration="+duration+"&customer_fname="+customer_fname+"&customer_lname="+customer_lname+"&customer_anum="+customer_anum;
                        //This is how you execute the post
            $http.post(url).then(function (response) {
                alert(String(response.data));
                });
            window.location.href = "index.html";
        }

        demoApp.cancel = () => {
            alert("Cancelled");
            window.location.href = "index.html";
        }


 });

