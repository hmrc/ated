ated
====

[![Build Status](https://travis-ci.org/hmrc/ated.svg?branch=master)](https://travis-ci.org/hmrc/ated)

Microservice for Annual Tax on Enveloped Dwellings. This implements the main business logic for ATED, communicating with ETMP(HOD) and Mongo Database for storage/retrieval. The microservice is based on the RESTful API structure, receives and sends data using JSON to either from.

All data received is validated against the relevant schema to ensure correct format of the data being received.


The APIs listed below are invoked for different operations from the frontend micro service. They are grouped according to the functionality.

## Relief Return APIs

### List of APIs

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
|```/ated/:atedRefNo/ated/reliefs/save``` | POST | saves the draft relief |
| ```/ated/:atedRefNo/ated/reliefs/:periodKey``` | GET | retrieve the draft relief based on period |
|```/ated/:atedRefNo/ated/reliefs/submit/:periodKey``` | GET | submit the draft return |
|```/ated/:atedRefNo/ated/reliefs/drafts``` | DELETE | delete the draft relief |
|```/ated/:atedRefNo/ated/reliefs/drafts/:periodKey``` | DELETE | delete the draft relief by year |

where,

| parameters | description |
|------|-------------------|
| periodKey | starting year of tax year (e.g. 2016 for '16-'17) |
| atedRefNo | unique identfier for clients subscribed to ATED |


### Usage with request and response

#### GET /ated/ATED1223123/ated/reliefs/2017```

> retrieve the draft relief based on period

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 404   | Not Found   |

**Response body**

[Relief Response](#relief-response)

#### POST /ated/ATED1223123/ated/reliefs/save```

> saves the draft relief

| Status | Message     |
|-------|-------------|
| 200   | Ok          |

**Example request with a valid body**

```json
{
	"atedRefNo": "ATED1223123",
	"periodKey": 2017,
	"reliefs": {
		"periodKey": 2017,
		"rentalBusiness": false,
		"openToPublic": false,
		"propertyDeveloper": false,
		"propertyTrading": false,
		"lending": false,
		"employeeOccupation": false,
		"farmHouses": false,
		"socialHousing": false,
		"equityRelease": false
	},
	"taxAvoidance": {
		"rentalBusinessScheme": "Scheme1",
		"rentalBusinessSchemePromoter": "Promoter1",
		"openToPublicScheme": "Scheme2",
		"openToPublicSchemePromoter": "Scheme2",
		"propertyDeveloperScheme": "Scheme3",
		"propertyDeveloperSchemePromoter": "Scheme3",
		"propertyTradingScheme": "Scheme4",
		"propertyTradingSchemePromoter": "Promoter4",
		"lendingScheme": "Scheme5",
		"lendingSchemePromoter": "Promoter5",
		"employeeOccupationScheme": "Scheme6",
		"employeeOccupationSchemePromoter": "Promoter6",
		"farmHousesScheme": "Scheme7",
		"farmHousesSchemePromoter": "Promoter7",
		"socialHousingScheme": "Scheme8",
		"socialHousingSchemePromoter": "Promoter8",
		"equityReleaseScheme": "Scheme9",
		"equityReleaseSchemePromoter": "Promoter9"
	},
	"periodStartDate": "2017-04-01",
	"periodEndDate": "2018-03-31",
	"timeStamp": {
		"$date": 1508239198714
	}
}
```
**Response body**

[Relief Response](#relief-response)

#### GET /ated/ATED1223123/ated/reliefs/submit/2017```

> submit the draft return

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 400   | Bad Request |
| 404   | Not Found   |
| 500   | Internal Server Error |
| 503   | Service Unavailable |

**Response body**

```json
{
	"processingDate": "2001-12-17T09:30:47Z",
	"liabilityReturnResponse": [{
		"mode": "Post",
		"propertyKey": "aaaaaaaaaa",
		"liabilityAmount": 1234,
		"paymentReference": "aaaaaaaaaaaaaa",
		"formBundleNumber": "012345678912"
	}]
}
```

#### DELETE /ated/ATED1223123/ated/reliefs/drafts```
 
> delete draft relief

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 500   | Internal Server Error |

**Response body**

[Relief Response](#relief-response)

#### DELETE /ated/ATED1223123/ated/reliefs/drafts/2017``` 

> delete draft relief by year

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 500   | Internal Server Error |

**Response body**

[Relief Response](#relief-response)

## Chargeable Return APIs

### List of APIs

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/ated/:atedRefNo/property-details/create/:periodKey``` | POST | create the draft chargeable property |
| ```/ated/:atedRefNo/property-details/retrieve/:id``` | GET | retrieve the draft chargeable property |
| ```/ated/:atedRefNo/property-details/address/:id``` | POST | update the draft chargeable property with address  |
| ```/ated/:atedRefNo/property-details/title/:id``` | POST | update the draft chargeable property with title  |
| ```/ated/:atedRefNo/property-details/has-value-change/:id``` | POST | update the draft chargeable property with change in value |
| ```/ated/:atedRefNo/property-details/acquisition/:id``` | POST | update the draft chargeable property with acquisition details |
| ```/ated/:atedRefNo/property-details/revalued/:id``` | POST | update the draft chargeable property with revalued details  |
| ```/ated/:atedRefNo/property-details/owned-before/:id``` | POST | update the draft chargeable property with owned-before details | 
| ```/ated/:atedRefNo/property-details/new-build/:id``` | POST | update the draft chargeable property with new-build details  |
| ```/ated/:atedRefNo/property-details/valued/:id``` | POST | update the draft chargeable property with valued details |
| ```/ated/:atedRefNo/property-details/full-tax-period/:id``` | POST | update the draft chargeable property for full tax period |  
| ```/ated/:atedRefNo/property-details/in-relief/:id``` | POST | update the draft chargeable property with in relief details |
| ```/ated/:atedRefNo/property-details/dates-liable/:id``` | POST | update the draft chargeable property with dates liable |
| ```/ated/:atedRefNo/property-details/dates-liable/add/:id``` | POST | add liable dates in the draft chargeable property |
| ```/ated/:atedRefNo/property-details/dates-in-relief/add/:id``` | POST | add in relief dates in the draft chargeable property |
| ```/ated/:atedRefNo/property-details/period/delete/:id``` | POST | delete a period in the draft chargeable property |
| ```/ated/:atedRefNo/property-details/tax-avoidance/:id``` | POST | update the draft chargeable property with tax-avoidance  |
| ```/ated/:atedRefNo/property-details/supporting-info/:id``` | POST | update the draft chargeable property with supporting-info  |
| ```/ated/:atedRefNo/property-details/calculate/:id``` | GET | calculate the draft chargeable property for mode = Pre-Calculation |
| ```/ated/:atedRefNo/property-details/submit/:id``` | POST | submit the draft chargeable property for mode = Post |

where,

| parameters | description |
|------|-------------------|
| id | chargeable return id |
| atedRefNo | unique identfier for clients subscribed to ATED |

### Usage with request and response

#### POST /ated/:atedRefNo/property-details/create/:periodKey

> create the draft chargeable property

**Example request with a valid body**

```json
{
	"line_1": "addr1",
	"line_2": "addr2",
	"line_3": "addr3",
	"line_4": "addr4",
	"postcode": "XX1 1XX"
}
```
**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### GET /ated/:atedRefNo/property-details/retrieve/:id

> retrieve the draft chargeable property

**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:atedRefNo/property-details/address/:id

> update the draft chargeable property with address

**Example request with a valid body**

```json
{
	"line_1": "addr1",
	"line_2": "addr2",
	"line_3": "addr3",
	"line_4": "addr4",
	"postcode": "XX1 1XX"
}
```
**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:atedRefNo/property-details/title/:id

> update the draft chargeable property with title

**Example request with a valid body**

```json
 {"titleNumber":"titleNo"}
```
**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:atedRefNo/property-details/has-value-change/:id`

> update the draft chargeable property with change in value

**Example request with a valid body**

```json
 Boolean
```
**Response body**

No body

#### POST /ated/:atedRefNo/property-details/acquisition/:id

> update the draft chargeable property with acquisition details

**Example request with a valid body**

```json
 Boolean
```
**Response body**

No body

> update the draft chargeable property with tax-avoidance

**Example request with a valid body**

```json
 {"isTaxAvoidance":true,"taxAvoidancePromoterReference":"12345678"}
```
**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

> update the draft chargeable property with supporting-info

**Example request with a valid body**

```json
 {"supportingInfo":"additional info"}
```
**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:atedRefNo/property-details/revalued/:id

> update the draft chargeable property with revalued details

```json
 {
 	"isPropertyRevalued": true,
 	"revaluedValue": 12000,
 	"revaluedDate": "2017-11-11",
 	"partAcqDispDate": "2017-11-12"
 }
```
**Response body**

[Response With Status Code](#response-with-status-code)

#### POST  /ated/:atedRefNo/property-details/new-build/:id

> update the draft chargeable property with new-build details

**Example request with a valid body**

```json
 {
 	"isNewBuild": true,
 	"newBuildValue": 1000,
 	"newBuildDate": "2017-11-11",
 	"localAuthRegDate": "2017-11-12",
 	"notNewBuildValue": 1000000000,
 	"notNewBuildDate": "2017-11-13"
 }
```
**Response body**

[Response With Status Code](#response-with-status-code)

#### POST ated/:atedRefNo/property-details/owned-before/:id 

> update the draft chargeable property with owned-before details

**Example request with a valid body**

```json
 {"isOwnedBefore2012":true,"ownedBefore2012Value":990000000}

```
**Response body**

[Response With Status Code](#response-with-status-code)

#### POST /ated/:atedRefNo/property-details/valued/:id

> update the draft chargeable property with valued details

**Example request with a valid body**

```json
 {"isValuedByAgent":true}

```
**Response body**

[Response With Status Code](#response-with-status-code)

#### POST /ated/:atedRefNo/property-details/full-tax-period/:id

> update the draft chargeable property for full tax period

**Example request with a valid body**

```json
{
  "isFullPeriod" : false
}
```

**Response body**

No body

#### POST /ated/:atedRefNo/property-details/in-relief/:id

> update the draft chargeable property with in relief details

**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:atedRefNo/property-details/dates-liable/:id

> update the draft chargeable property with dates liable

**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:atedRefNo/property-details/submit/:id

> submit the draft chargeable property for mode = Post

**Response body**
```json
{"processingDate": "2001-12-17T09:30:47Z", "atedRefNumber": "ABCDEabcde12345", "formBundleNumber": "123456789012345"}
```

#### GET /ated/:atedRefNo/property-details/calculate/:id`

> calculate the draft chargeable property for mode = Pre-Calculation

**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

## Client and Agent Registration Details APIs

### APIs for registering clients/agents 

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/ated/:atedRefNo/ated/details/:identifier/:identifierType``` | GET | retrieve ATED client registration details |
| ```/agent/:agentCode/ated/details/:identifier/:identifierType``` | GET | retrieve agent registration details |
| ```/ated/:atedRefNo/registration-details/:safeId``` | POST | update ATED client registration details |

where,

| parameters | description |
|------|-------------------|
| atedRefNo | unique identfier for clients subscribed to ATED |
| identifierType | arn or safeId or utr |
| identifier | the number for the above types |
| agentCode | agent code |

### Usage with request and response

#### POST /ated/:atedRefNo/registration-details/:safeId

> update ATED client registration details

**Example request with a valid body**

```json
{
	"isAnIndividual": false,
	"organisation": {
		"organisationName": "testName"
	},
	"address": {
		"addressLine1": "",
		"addressLine2": "",
		"countryCode": "GB"
	},
	"contactDetails": {},
	"isAnAgent": false,
	"isAGroup": false
}
```
**Response body**

[Agent Client Registration Response With Status Code](#gent-client-registration-response-with-status-code)

#### GET /agent/:agentCode/ated/details/:identifier/:identifierType

> retrieve agent registration details

**Response body**

[Agent Client Registration Response With Status Code](#agent-client-registration-response-with-status-code)

#### GET /agent/:agentCode/ated/details/:identifier/:identifierType

> retrieve ATED client registration details

**Response body**

[Agent Client Registration Response With Status Code](#gent-client-registration-response-with-status-code)


## Client and Agent Subscription Details APIs

### APIs for subscribing clients/agents 

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/ated/:atedRefNo/subscription-data ``` | GET | retrieve subscription data for client |
| ```/ated/:atedRefNo/subscription-data``` | POST | update subscription data for client |
| ```/agent/:agentCode/ated/subscription-data/:atedRefno``` | GET | retrieve subscription data for agent |


### Usage with request and response

#### GET /ated/:atedRefNo/subscription-data

> retrieve subscription data for client

**Response body**

```json
{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}
```

#### GET /agent/:agentCode/ated/subscription-data/:atedRefno

> retrieve subscription data for agent

**Response body**

```json
{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}
```

#### POST /ated/:atedRefNo/subscription-data

> update subscription data for client

**Example request with a valid body**

```json
{
  "emailConsent" : true,
  "changeIndicators" : {
    "nameChanged" : false,
    "permanentPlaceOfBusinessChanged" : false,
    "correspondenceChanged" : false,
    "contactDetailsChanged" : false
  },
  "address" : [ {
    "addressDetails" : {
      "addressType" : "Correspondence",
      "addressLine1" : "line1",
      "addressLine2" : "line2",
      "postalCode" : "postCode",
      "countryCode" : "GB"
    }
  } ]
}
```
**Response body**

```json
{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}
```

## Form-bundle return API

### APIs for retrieving return based on form-bundle number

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/ated/:atedRefNo/returns/form-bundle/:formBundleNumber``` | GET | retrieve submitted return details based on form-bundle number |

where,

| parameters | description |
|------|-------------------|
| formBundleNumber | return submission identifier from ETMP |

### Usage with request and response

#### GET /ated/:atedRefNo/returns/form-bundle/:formBundleNumber

> retrieve submitted return details based on form-bundle number

**Response body**
```json
{
  "periodKey": "2015",
  "propertyDetails": {
    "titleNumber": "CS72532",
    "address": {
      "addressLine1": "1 Whitehall Place",
      "addressLine2": "Virginia Water",
      "addressLine3": "Surrey",
      "postalCode": "GU254DG",
      "countryCode": "GB"
    },
    "additionalDetails": "additional additional"
  },
  "dateOfAcquisition": "2011-05-26",
  "valueAtAcquisition": 727000,
  "dateOfValuation": "2012-02-09",
  "taxAvoidanceScheme": "56485952",
  "taxAvoidancePromoterReference": "56485953",
  "ninetyDayRuleApplies": true,
  "professionalValuation": true,
  "localAuthorityCode": "1234",
  "dateOfSubmission": "2016-05-10",
  "liabilityAmount": 9375.12,
  "paymentReference": "abc456def123gh",
  "lineItem": [
    {
      "propertyValue": 727000,
      "dateFrom": "2015-04-01",
      "dateTo": "2016-01-01",
      "type": "Relief",
      "reliefDescription": "Property developers"
    }
  ]
}
```

## Summary return APIs

### APIs for retrieving summary returns

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/ated/:atedRefNo/returns/partial-summary``` | GET | retrieve partial-summary submitted return details based on form-bundle number |
| ```/ated/:atedRefNo/returns/full-summary``` | GET | retrieve full-summary submitted return details based on form-bundle number |

### Usage with request and response

#### GET /ated/:atedRefNo/returns/partial-summary

> retrieve partial-summary submitted return details based on form-bundle number

**Response body**
```json
{
  "safeId": "XA0001234567899",
  "organisationName": "Mark William LLP",
  "periodData": [
    {
      "periodKey": "2016",
      "returnData": {
        "reliefReturnSummary": [
          {
            "formBundleNumber": "123456789021",
            "dateOfSubmission": "2016-04-04",
            "relief": "Farmhouses",
            "reliefStartDate": "2016-04-01",
            "reliefEndDate": "2017-03-31",
            "arn": "JARN1234567",
            "taxAvoidanceScheme": "01234567",
            "taxAvoidancePromoterReference": "01234568"
          },
          {
            "formBundleNumber": "123456789022",
            "dateOfSubmission": "2016-04-04",
            "relief": "Property rental businesses",
            "reliefStartDate": "2016-04-01",
            "reliefEndDate": "2017-03-31",
            "arn": "JARN1234567",
            "taxAvoidanceScheme": "",
            "taxAvoidancePromoterReference": ""
          }
        ],
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "1 Whitehall Place",
                "addressLine2": "Aberdeen",
                "return": [
                  {
                    "formBundleNumber": "123456789019",
                    "dateOfSubmission": "2016-05-01",
                    "dateFrom": "2016-05-01",
                    "dateTo": "2017-01-01",
                    "liabilityAmount": "100.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789020",
                    "dateOfSubmission": "2016-04-01",
                    "dateFrom": "2017-01-02",
                    "dateTo": "2017-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789023",
                    "dateOfSubmission": "2016-04-01",
                    "dateFrom": "2017-01-02",
                    "dateTo": "2017-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": false
                  }
                ]
              }
            ]
          }
        ]
      }
    },
    {
      "periodKey": "2015",
      "returnData": {
        "reliefReturnSummary": [
          {
            "formBundleNumber": "123456789010",
            "dateOfSubmission": "2013-04-04",
            "relief": "Farmhouses",
            "reliefStartDate": "2015-04-01",
            "reliefEndDate": "2016-03-31",
            "arn": "JARN1234567",
            "taxAvoidanceScheme": "01234567",
            "taxAvoidancePromoterReference": "01234568"
          }
        ],
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "1 Whitehall Place",
                "addressLine2": "Aberdeen",
                "return": [
                  {
                    "formBundleNumber": "123456789011",
                    "dateOfSubmission": "2012-01-01",
                    "dateFrom": "2015-04-01",
                    "dateTo": "2016-01-01",
                    "liabilityAmount": "100.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789012",
                    "dateOfSubmission": "2014-01-01",
                    "dateFrom": "2016-01-02",
                    "dateTo": "2016-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789024",
                    "dateOfSubmission": "2014-01-01",
                    "dateFrom": "2016-01-02",
                    "dateTo": "2016-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": false
                  }
                ]
              }
            ]
          }
        ]
      }
    },
    {
      "periodKey": "2014",
      "returnData": {
        "reliefReturnSummary": [
          {
            "formBundleNumber": "123456789013",
            "dateOfSubmission": "2013-09-25",
            "relief": "Farmhouses",
            "reliefStartDate": "2014-04-01",
            "reliefEndDate": "2015-03-31",
            "arn": "JARN1234567",
            "taxAvoidanceScheme": "01234567",
            "taxAvoidancePromoterReference": "01234568"
          }
        ],
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "1 Lodge Lane",
                "addressLine2": "Liverpool",
                "return": [
                  {
                    "formBundleNumber": "123456789014",
                    "dateOfSubmission": "2013-03-03",
                    "dateFrom": "2014-04-01",
                    "dateTo": "2015-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  }
                ]
              }
            ]
          }
        ]
      }
    },
    {
      "periodKey": "2013",
      "returnData": {
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "Campden House Terrace",
                "addressLine2": "London",
                "return": [
                  {
                    "formBundleNumber": "123456789015",
                    "dateOfSubmission": "2012-04-04",
                    "dateFrom": "2013-04-01",
                    "dateTo": "2014-02-01",
                    "liabilityAmount": "300.33",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789016",
                    "dateOfSubmission": "2012-04-04",
                    "dateFrom": "2014-03-01",
                    "dateTo": "2014-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  }
                ]
              },
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "1 Montpelier Square",
                "addressLine2": "London",
                "return": [
                  {
                    "formBundleNumber": "123456789017",
                    "dateOfSubmission": "2012-04-11",
                    "dateFrom": "2013-04-01",
                    "dateTo": "2013-11-22",
                    "liabilityAmount": "100.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789018",
                    "dateOfSubmission": "2012-10-04",
                    "dateFrom": "2013-11-23",
                    "dateTo": "2014-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": false
                  }
                ]
              }
            ]
          }
        ]
      }
    },
    {
      "periodKey": "20  ",
      "returnData": {
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "                    ",
                "addressLine1": " ",
                "addressLine2": " ",
                "return": [
                  {
                    "formBundleNumber": "075000000164",
                    "dateOfSubmission": "2017-03-08",
                    "dateFrom": "2016-04-01",
                    "dateTo": "2017-03-31",
                    "liabilityAmount": 0,
                    "paymentReference": "XD002610105369",
                    "changeAllowed": false
                  }
                ]
              }
            ]
          }
        ]
      }
    }
  ],
  "atedBalance": "100.50"
}

```
#### GET /ated/:atedRefNo/returns/full-summary

> retrieve full-summary submitted return details based on form-bundle number

**Response body**
```json
{
  "safeId": "XA0001234567899",
  "organisationName": "Mark William LLP",
  "periodData": [
    {
      "periodKey": "2016",
      "returnData": {
        "reliefReturnSummary": [
          {
            "formBundleNumber": "123456789021",
            "dateOfSubmission": "2016-04-04",
            "relief": "Farmhouses",
            "reliefStartDate": "2016-04-01",
            "reliefEndDate": "2017-03-31",
            "arn": "JARN1234567",
            "taxAvoidanceScheme": "01234567",
            "taxAvoidancePromoterReference": "01234568"
          },
          {
            "formBundleNumber": "123456789022",
            "dateOfSubmission": "2016-04-04",
            "relief": "Property rental businesses",
            "reliefStartDate": "2016-04-01",
            "reliefEndDate": "2017-03-31",
            "arn": "JARN1234567",
            "taxAvoidanceScheme": "",
            "taxAvoidancePromoterReference": ""
          }
        ],
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "1 Whitehall Place",
                "addressLine2": "Aberdeen",
                "return": [
                  {
                    "formBundleNumber": "123456789019",
                    "dateOfSubmission": "2016-05-01",
                    "dateFrom": "2016-05-01",
                    "dateTo": "2017-01-01",
                    "liabilityAmount": "100.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789020",
                    "dateOfSubmission": "2016-04-01",
                    "dateFrom": "2017-01-02",
                    "dateTo": "2017-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789023",
                    "dateOfSubmission": "2016-04-01",
                    "dateFrom": "2017-01-02",
                    "dateTo": "2017-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": false
                  }
                ]
              }
            ]
          }
        ]
      }
    },
    {
      "periodKey": "2015",
      "returnData": {
        "reliefReturnSummary": [
          {
            "formBundleNumber": "123456789010",
            "dateOfSubmission": "2013-04-04",
            "relief": "Farmhouses",
            "reliefStartDate": "2015-04-01",
            "reliefEndDate": "2016-03-31",
            "arn": "JARN1234567",
            "taxAvoidanceScheme": "01234567",
            "taxAvoidancePromoterReference": "01234568"
          }
        ],
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "1 Whitehall Place",
                "addressLine2": "Aberdeen",
                "return": [
                  {
                    "formBundleNumber": "123456789011",
                    "dateOfSubmission": "2012-01-01",
                    "dateFrom": "2015-04-01",
                    "dateTo": "2016-01-01",
                    "liabilityAmount": "100.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789012",
                    "dateOfSubmission": "2014-01-01",
                    "dateFrom": "2016-01-02",
                    "dateTo": "2016-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789024",
                    "dateOfSubmission": "2014-01-01",
                    "dateFrom": "2016-01-02",
                    "dateTo": "2016-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": false
                  }
                ]
              }
            ]
          }
        ]
      }
    },
    {
      "periodKey": "2014",
      "returnData": {
        "reliefReturnSummary": [
          {
            "formBundleNumber": "123456789013",
            "dateOfSubmission": "2013-09-25",
            "relief": "Farmhouses",
            "reliefStartDate": "2014-04-01",
            "reliefEndDate": "2015-03-31",
            "arn": "JARN1234567",
            "taxAvoidanceScheme": "01234567",
            "taxAvoidancePromoterReference": "01234568"
          }
        ],
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "1 Lodge Lane",
                "addressLine2": "Liverpool",
                "return": [
                  {
                    "formBundleNumber": "123456789014",
                    "dateOfSubmission": "2013-03-03",
                    "dateFrom": "2014-04-01",
                    "dateTo": "2015-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  }
                ]
              }
            ]
          }
        ]
      }
    },
    {
      "periodKey": "2013",
      "returnData": {
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "Campden House Terrace",
                "addressLine2": "London",
                "return": [
                  {
                    "formBundleNumber": "123456789015",
                    "dateOfSubmission": "2012-04-04",
                    "dateFrom": "2013-04-01",
                    "dateTo": "2014-02-01",
                    "liabilityAmount": "300.33",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789016",
                    "dateOfSubmission": "2012-04-04",
                    "dateFrom": "2014-03-01",
                    "dateTo": "2014-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  }
                ]
              },
              {
                "contractObject": "12345678901234567890",
                "titleNumber": "Title number goes here",
                "addressLine1": "1 Montpelier Square",
                "addressLine2": "London",
                "return": [
                  {
                    "formBundleNumber": "123456789017",
                    "dateOfSubmission": "2012-04-11",
                    "dateFrom": "2013-04-01",
                    "dateTo": "2013-11-22",
                    "liabilityAmount": "100.12",
                    "paymentReference": "reference here",
                    "changeAllowed": true
                  },
                  {
                    "formBundleNumber": "123456789018",
                    "dateOfSubmission": "2012-10-04",
                    "dateFrom": "2013-11-23",
                    "dateTo": "2014-03-31",
                    "liabilityAmount": "1000.12",
                    "paymentReference": "reference here",
                    "changeAllowed": false
                  }
                ]
              }
            ]
          }
        ]
      }
    },
    {
      "periodKey": "20  ",
      "returnData": {
        "liabilityReturnSummary": [
          {
            "propertySummary": [
              {
                "contractObject": "                    ",
                "addressLine1": " ",
                "addressLine2": " ",
                "return": [
                  {
                    "formBundleNumber": "075000000164",
                    "dateOfSubmission": "2017-03-08",
                    "dateFrom": "2016-04-01",
                    "dateTo": "2017-03-31",
                    "liabilityAmount": 0,
                    "paymentReference": "XD002610105369",
                    "changeAllowed": false
                  }
                ]
              }
            ]
          }
        ]
      }
    }
  ],
  "atedBalance": "100.50"
}

```


## Edit submitted Chargeable Return APIs:

## Change submitted chargeable return APIs

### APIs for changing an already submitted return based on old form-bundle number

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/ated/:ated/liability-return/:oldFBNo``` | GET | retrieve chargeable return with old form bundle number for change |
| ```/ated/:ated/prev-liability-return/:oldFBNo/:period``` | GET | retrive chargeable return with old form bundle number and period |
| ```/ated/:ated/liability-return/:oldFBNo/update-has-bank``` | POST | update the chargeable return with has bank indicator |
| ```/ated/:ated/liability-return/:oldFBNo/update-bank``` | POST | update the chargeable return with has bank details |
| ```/ated/:ated/liability-return/calculate/:oldFBNo``` | GET | calculate the edited chargeable return |
| ```/ated/:ated/liability-return/:oldFBNo/submit``` | POST | submit the edited chargeable return |

where,

| parameters | description |
|------|-------------------|
| oldFBNo | last return submission identifier (from ETMP) |

### Usage with request and response

#### GET /ated/:ated/liability-return/:oldFBNo

> retrieve chargeable return with old form bundle number for change

**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### GET /ated/:ated/prev-liability-return/:oldFBNo/:period

> retrive chargeable return with old form bundle number and period

**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:ated/liability-return/:oldFBNo/update-has-bank

> update the chargeable return with has bank indicator

**Example request with a valid body**

```json
 Boolean
 ```

**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:ated/liability-return/:oldFBNo/update-bank

> update the chargeable return with has bank indicator

**Example request with a valid body**

```json
 Boolean
 ```

**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### GET /ated/:ated/liability-return/calculate/:oldFBNo

> calculate the edited chargeable return

**Response body**
```json
{
  "processingDate" : "2017-12-02T18:45:34Z",
  "liabilityReturnResponse" : [ ],
  "accountBalance" : 0
}

```
**Response body**

[Property Details Response With Status Code](#property-details-response-with-status-code)

#### POST /ated/:ated/liability-return/:oldFBNo/submit

**Response body with status code**
```json
{
  "processingDate" : "2017-12-02T18:45:34Z",
  "liabilityReturnResponse" : [ ],
  "accountBalance" : 0
}
```
| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 500   | Internal Server Error |


## Dispose submitted chargeable return APIs

### APIs for disposing an already submitted return based on old form-bundle number

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/ated/:ated/dispose-liability/:oldFBNo```| GET | retrieve chargeable return with old form bundle number for disposal |
| ```/ated/:ated/dispose-liability/:oldFBNo/update-date```| POST | update the chargeable return with date |
| ```/ated/:ated/dispose-liability/:oldFBNo/update-has-bank```| POST | update the chargeable return with has bank indicator |
| ```/ated/:ated/dispose-liability/:oldFBNo/update-bank``` | POST | update the chargeable return with has bank details |
| ```/ated/:ated/dispose-liability/:oldFBNo/calculate```|	GET	| calculate the edited chargeable return |
| ```/ated/:ated/dispose-liability/:oldFBNo/submit```|	POST | dispose the edited chargeable return |

where,

| parameters | description |
|------|-------------------|
| oldFBNo | last return submission identifier (from ETMP) |

### Usage with request and response

#### GET /ated/:ated/dispose-liability/:oldFBNo

> retrieve chargeable return with old form bundle number for disposal

**Response body with status code**
```json
{
	"atedRefNo": "ated-123",
	"id": "123456789012",
	"formBundleReturn": {
		"periodKey": "2015",
		"propertyDetails": {
			"titleNumber": "12345678",
			"address": {
				"addressLine1": "line1",
				"addressLine2": "line2",
				"countryCode": "GB"
			},
			"additionalDetails": "supportingInfo"
		},
		"dateOfValuation": "2015-05-05",
		"professionalValuation": true,
		"taxAvoidanceScheme": "taxAvoidanceScheme",
		"ninetyDayRuleApplies": true,
		"dateOfSubmission": "2015-05-05",
		"liabilityAmount": 123.23,
		"paymentReference": "payment-ref-123",
		"lineItem": [{
			"propertyValue": 5000000,
			"dateFrom": "2015-04-01",
			"dateTo": "2015-08-31",
			"type": "Liability"
		}, {
			"propertyValue": 5000000,
			"dateFrom": "2015-09-01",
			"dateTo": "2016-03-31",
			"type": "Relief",
			"reliefDescription": "Relief"
		}]
	},
	"timeStamp": 1512245001345
}
```

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 404   | Not Found   |


#### POST /ated/:ated/dispose-liability/:oldFBNo/update-date

> update the chargeable return with date

**Example request with a valid body**

```json
{
  "dateOfDisposal" : "2017-09-01",
  "periodKey" : 1234
}

```

**Response body with status code**

```json
{
	"atedRefNo": "ated-123",
	"id": "123456789012",
	"formBundleReturn": {
		"periodKey": "2015",
		"propertyDetails": {
			"titleNumber": "12345678",
			"address": {
				"addressLine1": "line1",
				"addressLine2": "line2",
				"countryCode": "GB"
			},
			"additionalDetails": "supportingInfo"
		},
		"dateOfValuation": "2015-05-05",
		"professionalValuation": true,
		"taxAvoidanceScheme": "taxAvoidanceScheme",
		"ninetyDayRuleApplies": true,
		"dateOfSubmission": "2015-05-05",
		"liabilityAmount": 123.23,
		"paymentReference": "payment-ref-123",
		"lineItem": [{
			"propertyValue": 5000000,
			"dateFrom": "2015-04-01",
			"dateTo": "2015-08-31",
			"type": "Liability"
		}, {
			"propertyValue": 5000000,
			"dateFrom": "2015-09-01",
			"dateTo": "2016-03-31",
			"type": "Relief",
			"reliefDescription": "Relief"
		}]
	},
	"disposeLiability": {
		"periodKey": 2015
	},
	"timeStamp": 1512245212621
}
```
| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 404   | Not Found   |

#### POST /ated/:ated/dispose-liability/:oldFBNo/update-has-bank

> update the chargeable return with has bank indicator

**Example request with a valid body**

```json
 Boolean
```

**Response body with status code**

```json
{
	"atedRefNo": "ated-123",
	"id": "123456789012",
	"formBundleReturn": {
		"periodKey": "2015",
		"propertyDetails": {
			"titleNumber": "12345678",
			"address": {
				"addressLine1": "line1",
				"addressLine2": "line2",
				"countryCode": "GB"
			},
			"additionalDetails": "supportingInfo"
		},
		"dateOfValuation": "2015-05-05",
		"professionalValuation": true,
		"taxAvoidanceScheme": "taxAvoidanceScheme",
		"ninetyDayRuleApplies": true,
		"dateOfSubmission": "2015-05-05",
		"liabilityAmount": 123.23,
		"paymentReference": "payment-ref-123",
		"lineItem": [{
			"propertyValue": 5000000,
			"dateFrom": "2015-04-01",
			"dateTo": "2015-08-31",
			"type": "Liability"
		}, {
			"propertyValue": 5000000,
			"dateFrom": "2015-09-01",
			"dateTo": "2016-03-31",
			"type": "Relief",
			"reliefDescription": "Relief"
		}]
	},
	"bankDetails": {
		"hasBankDetails": true,
		"bankDetails": {}
	},
	"timeStamp": 1512245478849
}
```

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 404   | Not Found   |


#### POST /ated/:ated/dispose-liability/:oldFBNo/update-bank

> update the chargeable return with has bank details

**Response body with status code**

```json
{
	"atedRefNo": "ated-123",
	"id": "123456789012",
	"formBundleReturn": {
		"periodKey": "2015",
		"propertyDetails": {
			"titleNumber": "12345678",
			"address": {
				"addressLine1": "line1",
				"addressLine2": "line2",
				"countryCode": "GB"
			},
			"additionalDetails": "supportingInfo"
		},
		"dateOfValuation": "2015-05-05",
		"professionalValuation": true,
		"taxAvoidanceScheme": "taxAvoidanceScheme",
		"ninetyDayRuleApplies": true,
		"dateOfSubmission": "2015-05-05",
		"liabilityAmount": 123.23,
		"paymentReference": "payment-ref-123",
		"lineItem": [{
			"propertyValue": 5000000,
			"dateFrom": "2015-04-01",
			"dateTo": "2015-08-31",
			"type": "Liability"
		}, {
			"propertyValue": 5000000,
			"dateFrom": "2015-09-01",
			"dateTo": "2016-03-31",
			"type": "Relief",
			"reliefDescription": "Relief"
		}]
	},
	"bankDetails": {
		"hasBankDetails": true,
		"bankDetails": {}
	},
	"timeStamp": 1512245850965
}
```

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 404   | Not Found   |

#### GET /ated/:ated/dispose-liability/:oldFBNo/calculate

> calculate the edited chargeable return

**Response body with status code**

```json
{
	"atedRefNo": "ated-123",
	"id": "123456789012",
	"formBundleReturn": {
		"periodKey": "2015",
		"propertyDetails": {
			"titleNumber": "12345678",
			"address": {
				"addressLine1": "line1",
				"addressLine2": "line2",
				"countryCode": "GB"
			},
			"additionalDetails": "supportingInfo"
		},
		"dateOfValuation": "2015-05-05",
		"professionalValuation": true,
		"taxAvoidanceScheme": "taxAvoidanceScheme",
		"ninetyDayRuleApplies": true,
		"dateOfSubmission": "2015-05-05",
		"liabilityAmount": 123.23,
		"paymentReference": "payment-ref-123",
		"lineItem": [{
			"propertyValue": 5000000,
			"dateFrom": "2015-04-01",
			"dateTo": "2015-08-31",
			"type": "Liability"
		}, {
			"propertyValue": 5000000,
			"dateFrom": "2015-09-01",
			"dateTo": "2016-03-31",
			"type": "Relief",
			"reliefDescription": "Relief"
		}]
	},
	"bankDetails": {
		"hasBankDetails": true,
		"bankDetails": {}
	},
	"timeStamp": 1512245850965
}
```

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 404   | Not Found   |

#### POST /ated/:ated/dispose-liability/:oldFBNo/submit

> dispose the edited chargeable return

**Response body with status code**
```json
{
	"atedRefNo": "ated-123",
	"id": "123456789012",
	"formBundleReturn": {
		"periodKey": "2015",
		"propertyDetails": {
			"titleNumber": "12345678",
			"address": {
				"addressLine1": "line1",
				"addressLine2": "line2",
				"countryCode": "GB"
			},
			"additionalDetails": "supportingInfo"
		},
		"dateOfValuation": "2015-05-05",
		"professionalValuation": true,
		"taxAvoidanceScheme": "taxAvoidanceScheme",
		"ninetyDayRuleApplies": true,
		"dateOfSubmission": "2015-05-05",
		"liabilityAmount": 123.23,
		"paymentReference": "payment-ref-123",
		"lineItem": [{
			"propertyValue": 5000000,
			"dateFrom": "2015-04-01",
			"dateTo": "2015-08-31",
			"type": "Liability"
		}, {
			"propertyValue": 5000000,
			"dateFrom": "2015-09-01",
			"dateTo": "2016-03-31",
			"type": "Relief",
			"reliefDescription": "Relief"
		}]
	},
	"timeStamp": 1512245001345
}
```

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 404   | Not Found   |



#### Relief Response

```json
 [{
 	"atedRefNo": "ATED1223123",
 	"periodKey": 2017,
 	"reliefs": {
 		"periodKey": 2017,
 		"rentalBusiness": false,
 		"openToPublic": false,
 		"propertyDeveloper": false,
 		"propertyTrading": false,
 		"lending": false,
 		"employeeOccupation": false,
 		"farmHouses": false,
 		"socialHousing": false,
 		"equityRelease": false
 	},
 	"taxAvoidance": {
 		"rentalBusinessScheme": "Scheme1",
 		"rentalBusinessSchemePromoter": "Promoter1",
 		"openToPublicScheme": "Scheme2",
 		"openToPublicSchemePromoter": "Scheme2",
 		"propertyDeveloperScheme": "Scheme3",
 		"propertyDeveloperSchemePromoter": "Scheme3",
 		"propertyTradingScheme": "Scheme4",
 		"propertyTradingSchemePromoter": "Promoter4",
 		"lendingScheme": "Scheme5",
 		"lendingSchemePromoter": "Promoter5",
 		"employeeOccupationScheme": "Scheme6",
 		"employeeOccupationSchemePromoter": "Promoter6",
 		"farmHousesScheme": "Scheme7",
 		"farmHousesSchemePromoter": "Promoter7",
 		"socialHousingScheme": "Scheme8",
 		"socialHousingSchemePromoter": "Promoter8",
 		"equityReleaseScheme": "Scheme9",
 		"equityReleaseSchemePromoter": "Promoter9"
 	},
 	"periodStartDate": "2017-04-01",
 	"periodEndDate": "2018-03-31",
 	"timeStamp": {
 		"$date": 1508239198714
 	}
 }]
```

#### Property Details Response With Status Code

| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 400   | Bad Request |
| 404   | Not Found   |
| 500   | Internal Server Error |
| 503   | Service Unavailable |

```json
{
  "atedRefNo" : "ated-ref-123",
  "id" : "123456789012",
  "periodKey" : 2015,
  "addressProperty" : {
    "line_1" : "addr1",
    "line_2" : "addr2",
    "line_3" : "addr3",
    "line_4" : "addr4"
  },
  "title" : {
    "titleNumber" : "titleNo"
  },
  "value" : {
    "anAcquisition" : true,
    "isPropertyRevalued" : true,
    "revaluedValue" : 1111.11,
    "revaluedDate" : "1970-01-01",
    "isOwnedBefore2012" : true,
    "ownedBefore2012Value" : 1111.11,
    "isNewBuild" : true,
    "newBuildValue" : 1111.11,
    "newBuildDate" : "1970-01-01",
    "notNewBuildValue" : 1111.11,
    "notNewBuildDate" : "1970-01-01",
    "isValuedByAgent" : true
  },
  "period" : {
    "isFullPeriod" : false,
    "isTaxAvoidance" : true,
    "taxAvoidanceScheme" : "taxAvoidanceScheme",
    "taxAvoidancePromoterReference" : "taxAvoidancePromoterReference",
    "supportingInfo" : "supportingInfo",
    "isInRelief" : true,
    "liabilityPeriods" : [ {
      "lineItemType" : "Liability",
      "startDate" : "2015-04-01",
      "endDate" : "2015-08-31"
    } ],
    "reliefPeriods" : [ {
      "lineItemType" : "Relief",
      "startDate" : "2015-09-01",
      "endDate" : "2016-03-31",
      "description" : "Relief"
    } ]
  },
  "calculated" : {
    "valuationDateToUse" : "1970-01-01",
    "professionalValuation" : true,
    "liabilityPeriods" : [ {
      "value" : 1111.11,
      "startDate" : "2015-04-01",
      "endDate" : "2015-08-31",
      "lineItemType" : "Liability"
    } ],
    "reliefPeriods" : [ {
      "value" : 1111.11,
      "startDate" : "2015-09-01",
      "endDate" : "2016-03-31",
      "lineItemType" : "Relief",
      "description" : "Relief"
    } ],
    "timeStamp" : 1512238927440
  },
  "timeStamp" : {
    "$date" : 1512238927440
  }
}

```

#### Agent Client Registration Response With Status Code

For agents,
```json
{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}
```

For clients,
```json
{"sapNumber":"1234567890", "safeId": "EX0012345678909"}
```
| Status | Message     |
|-------|-------------|
| 200   | Ok          |
| 400   | Bad Request |
| 404   | Not Found   |
| 500   | Internal Server Error |
| 503   | Service Unavailable |

#### Response With Status Code

| Status | Message     |Body         |
|-------|-------------|-------------|
| 200   | Ok          |             |
| 400   | Bad Request | Invalid Request |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
