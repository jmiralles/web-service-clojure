(ns example-project.handler
  (:require [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [ring.util.response :as ring-response]
            [ring.util.request :as ring-request]
            [compojure.route :as route]
            [cheshire.core :as json]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))


(defn wrap-500-catchall
  "Wrap the given handler in a try/catch expression, returning a 500 response if any exceptions are caught."
  [handler] ;; we want to take a handler as a parameter
  (fn [request] ;; we're also returning a handler
    (try (handler request)
     ;; middleware almost always calls the handler that's passed in 
         (catch Exception e
           (-> (ring-response/response (.getMessage e)) ;; place the exception's message in the body 
               (ring-response/status 500)
               ;; set the status code to 500 
               (ring-response/content-type "text/plain")
               ;; there's no HTML here, so we'll use text/plain 
               (ring-response/charset "utf-8")
               ;; and update the character set
               )))))


(defn wrap-slurp-body [handler]
  (fn [request]
    (if (instance? java.io.InputStream (:body request))
      (let [prepared-request (update request :body slurp)]
        (handler prepared-request))
      (handler request))))


(defn body-echo-handler
  [request]
  (if-let [body (:body request)]
    ;; remember, not all requests have bodies!
    (-> (ring-response/response body)
        (ring-response/content-type "text/plain")
        (ring-response/charset "utf-8"))
    ;; if there's no body, let's call this a 400 (Bad request)
    (-> (ring-response/response "You must submit a body with your request!")
        (ring-response/status 400))))


(def body-echo-app
  (-> body-echo-handler
      wrap-500-catchall
      wrap-slurp-body))


(defn echo ;; based on echo-body-handler 
  [body]
  (if (not-empty body) ;; excludes nil and the empty string 
    (-> (ring-response/response body)
        (ring-response/content-type "text/plain")
        (ring-response/charset "utf-8"))
     ;; if there's no body, let's call this a 400 Malformed
    (-> (ring-response/response "You must submit a body with your request!")
        (ring-response/status 400))))


(def handle-info
  (ring-json/wrap-json-response
   (fn [_] ;; we don't actually need the request
     (-> {"Java Version" (System/getProperty "java.version")
          "OS Name" (System/getProperty "os.name")
          "OS Version" (System/getProperty "os.version")}
         ring-response/response))))


(defn handle-clojurefy
  [request]
  (-> (:body request) ;; extract the body of the request
      str ;; turn it into a string
      ring-response/response ;; wrap it in a response map 
      (ring-response/content-type "application/edn")))


(defn wrap-json [handler]
  (fn [request]
    (if-let [prepd-request (try (update request :body json/decode)
                                (catch com.fasterxml.jackson.core.JsonParseException e
                                  nil))] (handler prepd-request)
;; we'll only get here if there's a parse error
            (-> (ring-response/response "Sorry, that's not JSON.")
                (ring-response/status 400)))))


(def body-routes
  (-> (routes
       (ANY "/echo" [:as {body :body}] (echo body)))
      (wrap-routes wrap-slurp-body)))


(defroutes non-body-routes
  (GET "/" [] "Hello World")
  (GET "/trouble" [] (/ 1 0)) ;; this won't end well!
  (GET "/links/:id" [id] (str "The id is: " id))
  (GET "/info" [] handle-info)
  (route/not-found "Not Found"))



(def json-routes
  (routes
   (POST "/clojurefy" [] (ring-json/wrap-json-body handle-clojurefy))))


(def app-routes
  (routes json-routes body-routes non-body-routes))


(def app
  (-> app-routes
      wrap-500-catchall
      (wrap-defaults api-defaults)))