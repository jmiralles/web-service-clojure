(ns example-project.handler
  (:require [compojure.core :refer :all]
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
           (-> (ring.util.response/response (.getMessage e)) ;; place the exception's message in the body 
               (ring.util.response/status 500)
               ;; set the status code to 500 
               (ring.util.response/content-type "text/plain")
               ;; there's no HTML here, so we'll use text/plain 
               (ring.util.response/charset "utf-8")
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
    (-> (ring.util.response/response body)
        (ring.util.response/content-type "text/plain")
        (ring.util.response/charset "utf-8"))
    ;; if there's no body, let's call this a 400 (Bad request)
    (-> (ring.util.response/response "You must submit a body with your request!")
        (ring.util.response/status 400))))

(def body-echo-app
  (-> body-echo-handler
      wrap-500-catchall
      wrap-slurp-body))

(defn echo ;; based on echo-body-handler 
  [body]
  (if (not-empty body) ;; excludes nil and the empty string 
    (-> (ring.util.response/response body)
        (ring.util.response/content-type "text/plain")
        (ring.util.response/charset "utf-8"))
     ;; if there's no body, let's call this a 400 Malformed
    (-> (ring.util.response/response "You must submit a body with your request!")
        (ring.util.response/status 400))))


(defn wrap-json [handler]
  (fn [request]
    (if-let [prepd-request
             (try (update request :body json/decode)
                  (catch com.fasterxml.jackson.core.JsonParseException e
                    nil))]
      (handler prepd-request)
       ;; we'll only get here if there's a parse error
      (-> (ring.util.response/response "Sorry, that's not JSON.")
          (ring.util.response/status 400)))))

(defn handle-clojurefy
  [request]
  (-> (:body request) ;; extract the body of the request
      str ;; turn it into a string
      ring.util.response/response ;; wrap it in a response map 
      (ring.util.response/content-type "application/edn")))


(def body-routes
  (-> (routes
       (ANY "/echo" [:as {body :body}] (echo body))
       (POST "/clojurefy" [] (wrap-json handle-clojurefy)))
      wrap-slurp-body))

(defroutes non-body-routes
  (GET "/" [] "Hello World")
  (GET "/trouble" [] (/ 1 0)) ;; this won't end well!
  (GET "/links/:id" [id] (str "The id is: " id))
  (route/not-found "Not Found"))

(def app-routes
  (routes body-routes non-body-routes))


(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      wrap-slurp-body
      wrap-500-catchall))


