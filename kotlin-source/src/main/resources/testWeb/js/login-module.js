"use strict";

//Main JS App for Term Deposits
const app = angular.module('loginAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('LoginAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    document.getElementById("loading").style.display = "none"
    //Show popup login
//    function displayNodeName() {
//        //Make the API Call then change the title
//        var url = "/api/example/me"
//        $http.get(url).then(function (response) {
//            var title = document.getElementById("title").innerHTML = "TD Cordapp: " + extractOrganisationName(response.data.me);
//            var name_title = document.getElementById("node_title").innerHTML = "Welcome, " + extractOrganisationName(response.data.me);
//            var img = document.createElement('img');
//            if (extractOrganisationName(response.data.me) == "AMM") {
//                img.src = 'img/' + 'amm_logo.png';
//                document.getElementById('img_header').appendChild(img)
//            } else if (extractOrganisationName(response.data.me) == "BankA" || name_title == "BankB") {
//                img.src = 'img/' + 'commbank_logo.png';
//                document.getElementById('img_header').appendChild(img)
//            }
//        });
//    }

    //TODO: Reprompt on no user/pw once a way to do user sessions is figured out.
    demoApp.login = () => {
        var username = document.getElementById("username").value;
        var password = document.getElementById("password").value;
        //alert("Tried to login");
        //Submit the password
        var url = "/api/auth/login?username="+username+"&password="+password
        document.getElementById("loading").style.display = "block"
        $http.post(url).then(function (response) {
            if (response.status != 202) {
                alert("Login failed");
                document.getElementById("loading").style.display = "none"
            } else {
                alert(String(response.data));
                window.location.href = "homepage.html";
                document.getElementById("loading").style.display = "none"
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


});