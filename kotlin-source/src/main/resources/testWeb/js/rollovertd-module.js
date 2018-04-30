"use strict";

//App Module for redeeming a term deposit
const app = angular.module('RolloverTDAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('RolloverTDAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    // We identify the node.
    const apiBaseURL = "/api/example/";
    document.getElementById("loading").style.display = "none"
    //populate the current offer options
    var matured = [];
    var offers = [];
    getMatured();
    getOffers();

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

    //Get all matured term deposits (for our demo purposes this is just any active td)
    function getMatured() {
        $http.get("/api/term_deposits/deposits").then(function (response) {
            response.data.states.forEach(function (element) {
                if (String(element.internalState) == "Active" || String(element.internalState) == "Matured") { //active kept in for demo purposes
                    matured.push(element);
                }
            });
            loadMatured();
        });
    }

    //Display matured term deposits to the user
    function loadMatured() {
        var matured_select = document.getElementById("matured_select");
        for (var i = 0; i < matured.length; i++) {
            var option = document.createElement("option");
            option.value = (matured[i]);
            option.innerHTML = "To: " + String(matured[i].to) + "\nEnd Date: "+
                    String(matured[i].endDate) + "\nAmount: "+ String(matured[i].amount) + "\nInterest: "+
                    String(matured[i].percent);
            matured_select.appendChild(option);
        }
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
        var offers_select = document.getElementById("deposit_offers")
            for (var i = 0; i < offers.length; i++) {
                var option = document.createElement("option");
                option.value = (offers[i]);
                option.innerHTML = "Issuing Institute: " + String(offers[i].issuingInstitute) + "\nValid Till: "+
                        String(offers[i].validTill) + "\nDuration: "+ String(offers[i].duration) + "\nInterest: "+
                        String(offers[i].interest);
                offers_select.appendChild(option);
            }
    }


    //OnClick method for redeem button
    demoApp.rollover = () => {
        //Load in the required data
        var matured_select = document.getElementById("matured_select");
        var selectedDeposit = matured[matured_select.selectedIndex];
        var new_deposit = document.getElementById("deposit_offers");
        var selectedNewDeposit = offers[new_deposit.selectedIndex];
        var with_interest_select = document.getElementById("with_interest");
        //Parse options selected and pull the data
        var value = selectedDeposit.amount;
        var actualValue = stripValue(value); //removes the USD from the string and returns the int
        var offering_institute = extractOrganisationName(selectedDeposit.from);
        var client = extractOrganisationName(selectedDeposit.to);
        var interest_percent = selectedDeposit.percent;
        var duration = getDuration(selectedDeposit.startDate, selectedDeposit.endDate);
        var customer_anum = selectedDeposit.client.id;
        var startDate = selectedDeposit.startDate;
        //Parse the selected new deposit
        var new_interest = selectedNewDeposit.interest;
        var new_institute = extractOrganisationName(selectedNewDeposit.issuingInstitute);
        var new_duration = selectedNewDeposit.duration;
        //Parse the selected with interest option
        var selectedWithInterest = with_interest_select.value;

        $http.get("/api/term_deposits/kyc").then(function (response) {
            response.data.kyc.forEach(function (element) {
                if (String(element.uniqueIdentifier.id) == customer_anum) {
                    //This is the correct client
                    callRollover(actualValue, offering_institute, interest_percent, duration, element.firstName, element.lastName,
                    element.accountNum, startDate, client, new_interest, new_institute, new_duration, selectedWithInterest);
                    return;
                }
             });
        });
    }

    //Redeem API call
    function callRollover(value, offering_institute, interest_percent, duration, customer_fname, customer_lname, customer_anum, startDate, client,
            new_interest, new_institute, new_duration, with_interest) {
        var url = "/api/term_deposits/rollover_td?td_value="+value+"&offering_institute="+offering_institute+"&interest_percent="+interest_percent+
        "&duration="+duration+"&customer_fname="+customer_fname+"&customer_lname="+customer_lname+"&customer_anum="+customer_anum+
        "&start_date="+startDate+"&client="+client+"&new_interest="+new_interest+"&new_institute="+new_institute+"&new_duration="+new_duration+
        "&with_interest="+with_interest;
            //Display a loading circle
        document.getElementById("loading").style.display = "block"
        $http.post(url).then(function successCallback(response) {
            document.getElementById("loading").style.display = "none"
            alert(String(response.data));
            window.location.href = "index.html";
        }, function errorCallback(error) {
            alert(String(error.data));
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

    function getCustomerName(accountNum) {
                var array = [];
                $http.get("/api/term_deposits/kyc").then(function (response) {
                    response.data.kyc.forEach(function (element) {
                        if (String(element.uniqueIdentifier.id) == accountNum) {
                            //This is the correct client
                            array.push(element.firstName);
                            array.push(element.lastName);
                            return array;
                        } else {
                            //alert(element.uniqueIdentifier.id)
                        }
                    });
                });
            }

    /** Since JS uses a different min date to Java, a custom duration calcualtor needs to be made */
    function getDuration(startDate, endDate) {
        //Note startDate and end date are strings in the form YYYY-MM-DD
        //Parse both strings
        var startDateYears = parseInt(parseYear(startDate));
        var startDateMonths = parseInt(parseMonth(startDate));
        var endDateYears = parseInt(parseYear(endDate));
        var endDateMonths = parseInt(parseMonth(endDate));

        var diff = (Math.abs(endDateYears - startDateYears) * 12) + Math.abs(endDateMonths - startDateMonths);
        return diff;

    }

    function parseYear(dateString) {
        for (var i = 1; i < dateString.length; i++) {
        //year is all strings before the first "-" -> start at 1 due to the first char being + or - which we can always include
            if (dateString[i] == '-') {
                //found the end of the year section
                return dateString.substring(0,i);
            }
        }
    }

    function parseMonth(dateString) {
        //first get past the first '-'
        var i = 1;
        while (dateString[i] != '-' && i < dateString.length) {
            i++;
        }
        //i is now equal to the index of first '-', increment once for start index of substring
        var startIndex = ++i;
        //get the end index
        while (dateString[i] != '-' && i < dateString.length) {
            i++;
        }
        //i is now equal to the index of first '-', increment once for start index of substring
        var endIndex = i;
        return dateString.substring(startIndex, endIndex);
    }

    function parseDay(dateString) {
        //first get past the first and second '-'
        var i = 1;
        var count = 0;
        while (count != 2 && i < dateString.length) {
            if (dateString[i] == '-') {
                count++;
            }
            i++;
         }
         //i is now equal to the index of second '-', increment once for start index of substring
         var startIndex = ++i;
         //get the end index
         while (dateString[i] != '\n' && i < dateString.length) {
            i++;
         }
         //i is now equal to the index of first '-', increment once for start index of substring
         var endIndex = i;
         return dateString.substring(startIndex, endIndex);
    }

    function stripValue(value) {

        var endIndex = 0;
        for (var i = 0; i < value.length; i++) {
            if (isNaN(value[i]) && value[i] != '.') {
                endIndex = i;
                i = value.length;
            } else {
                //found the end index
                i++;
            }
        }
        return parseInt(value.substring(0,endIndex));
    }



});

