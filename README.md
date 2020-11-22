
  
### Getting Started  
##### Update resources/project-config.edn
Update the `:connection` and `:db-name` keys with your Datomic Cloud configuration
```clojure
{:db-name    "ws-demo"
 :connection {:server-type :ion
              :region      "eu-west-1"
              :system      "knox"
              :endpoint    "http://entry.knox.eu-west-1.datomic.net:8182/"
              :proxy-port  8182}}
```

##### Database setup
Create a new Datomic database (if necessary)
```clojure
(in-ns 'wsdemo.client)
(d/create-database (get-conn) {:db-name "ws-demo"})
```
And transact the demo schema:
```clojure
(d/transact (get-conn) {:tx-data (-> "schema.edn" io/resource slurp read-string)})
```

##### Deploy lambda functions
The project uses Datomic Ions to deploy 3 Clojure Lambda functions which handle websocket connections via an AWS API Gateway:

`wsdemo.handlers/gateway-authorizer`
(Re)authorizes connections to the gateway

`wsdemo.handlers/handle-connection-proxy`
Links one or more gateway connection IDs with database users

`wsdemo.io/handle-message-proxy`
Handles all communications after a successful connection

Deploy them to your AWS infrastructure
```bash
$ clj -Adeploy-to-aws <system-name>
```

##### API Gateway

In the AWS Console, create a new WebSocket API
* `Route Selection Expression` = `$request.body.event` (default)
* `$connect`
    * Route Request
        * Authorizer = `gateway-authorizer` Ion
    * Integration Response
        * Integration type: Lambda Function
        * Use Lambda Proxy Integration
        * Lambda function = `ws-handle-connection-proxy`
* `$disconnect`
    * Integration Response
        * Integration type: Lambda Function
        * Use Lambda Proxy Integration
        * Lambda function = `ws-handle-connection-proxy`
* `$default`
    * Integration Response
        * Integration type: Lambda Function
        * Use Lambda Proxy Integration
        * Lambda function = `ws-handle-message-proxy`
 
Deploy the API, and then update `resources/project-config.edn`
```clojure
{:api-gw {:hostname "n3giprj0y8.execute-api.eu-west-1.amazonaws.com"
          :stage    "dev"
          :path     "/dev/@connections/"}}
```
### Testing  
##### REPL
The `wsdemo.test-connections` namespace will estabish two connections when loaded. The `broadcast-from-conn-1` function will send a message from `conn-1` to all connections, in this case `conn-1` and `conn-2`:
```clojure
(in-ns 'wsdemo.test-connections)
(broadcast-from-conn-1)
Message received: {:message Hello everyone, :sender eb50a8b9-f285-4ca7-ac99-0c876a5daa5d}
Message received: {:message Hello everyone, :sender eb50a8b9-f285-4ca7-ac99-0c876a5daa5d}
```
##### wscat
Install wscat
```bash  
$ yarn global add wscat  
```  
Get a valid JWT sign with the secret from `project-config.edn`
```clojure
(in-ns 'wsdemo.test-connections)
(jwt/sign {:sub (UUID/randomUUID)} secret)
=>
"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwZmEyNTVkZS1jOGJkLTQ0M2EtODY0Yy1jODkyZGFmY2ZjNTgifQ.ZMcAEwd3JHP7HqBnprvz_38uPtNmp_cNl14mGCI1E_U"
```
Connect and broadcast a message
```bash
$ wscat -c wss://n3giprj0y8.execute-api.eu-west-1.amazonaws.com/dev -H Authorization:<JWT>
> {:action :broadcast :message "Hello everyone"}
< {:message "Hello everyone", :sender "0fa255de-c8bd-443a-864c-c892dafcfc58"}
```