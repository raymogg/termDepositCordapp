"use strict";

//App module for activating a term deposit
//NOTE: Activating a TD can only be done by the issuing node, if another node attempts this the API call will fail.
const app = angular.module('ActivateTDAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('ActivateTDAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    document.getElementById("loading").style.display = "none"
    //populate the current offer options
    var pending = [];
    getPending();

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

    //Get all pending term deposits
    function getPending() {
        $http.get("/api/term_deposits/deposits").then(function (response) {
            response.data.states.forEach(function (element) {
                if (String(element.internalState) == "Pending") {
                    pending.push(element);
                }
            });
            loadPending();
        });
    }

    //Show the user all available pending term deposits
    function loadPending() {
        var pending_select = document.getElementById("pending_select");
        for (var i = 0; i < pending.length; i++) {
            var option = document.createElement("option");
            option.value = (pending[i]);
            option.innerHTML = "To: " + String(pending[i].to) + "\nEnd Date: "+
                    String(pending[i].endDate) + "\nAmount: "+ String(pending[i].amount) + "\nInterest: "+
                    String(pending[i].percent);
            pending_select.appendChild(option);
        }
    }

    //OnClick method for activation
    demoApp.confirmActivate = () => {
        //Load in the required data
        var pending_selected = document.getElementById("pending_select");
        var selectedDeposit = pending[pending_selected.selectedIndex];
        //Parse options selected and pull the data
        var value = selectedDeposit.amount;
        var actualValue = stripValue(value); //removes the USD from the string and returns the int
        var offering_institute = extractOrganisationName(selectedDeposit.from);
        var client = extractOrganisationName(selectedDeposit.to);
        var interest_percent = selectedDeposit.percent;
        var duration = getDuration(selectedDeposit.startDate, selectedDeposit.endDate);
        var customer_anum = selectedDeposit.client.id;
        //TODO: Should we make an API query for details via account num, or should this be done by iterating in javascript? for now second option
        var startDate = selectedDeposit.startDate;
        $http.get("/api/term_deposits/kyc").then(function (response) {
            response.data.kyc.forEach(function (element) {
                if (String(element.uniqueIdentifier.id) == customer_anum) {
                    //This is the correct client
                    callActivate(actualValue, offering_institute, interest_percent, duration, element.firstName, element.lastName,
                    element.accountNum, startDate, client);
                    return;
                }
            });
        });
    }

        demoApp.cancel = () => {
            alert("Cancelled");
            window.location.href = "index.html";
        }

        //Actual API call for activating a term deposit. On sucess returns user to the home page
        function callActivate(value, offering_institute, interest_percent, duration, customer_fname, customer_lname, customer_anum, startDate, client) {
            var url = "/api/term_deposits/activate_td?td_value="+value+"&offering_institute="+offering_institute+"&interest_percent="+interest_percent+
                          "&duration="+duration+"&customer_fname="+customer_fname+"&customer_lname="+customer_lname+"&customer_anum="+customer_anum+"&start_date="+startDate+
                          "&client="+client;
                          //Display a loading circle
            document.getElementById("loading").style.display = "block"
            $http.post(url).then(function (response) {
                document.getElementById("loading").style.display = "none"
                alert(String(response.data));
                window.location.href = "index.html";
            });
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

