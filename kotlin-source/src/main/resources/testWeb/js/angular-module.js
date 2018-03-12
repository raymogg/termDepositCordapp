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

let myArray = ["one", "two", "three"];

function getActiveDeposits() {
  var ul = document.createElement('ul');
   ul.setAttribute('id','proList');

   var t, tt;
   document.getElementById('currentDeposits').appendChild(ul);
   myArray.forEach(renderProductList);

   function renderProductList(element, index, arr) {
       var li = document.createElement('li');
       li.setAttribute('class','item');

       ul.appendChild(li);

       t = document.createTextNode(element);

       li.innerHTML=li.innerHTML + element;
   }
}

function clickButton() {
  alert("Hello");
}

getActiveDeposits();
