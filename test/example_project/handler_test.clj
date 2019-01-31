(ns example-project.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [cheshire.core :as json]
            [example-project.handler :refer :all]))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "Hello World"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404))))

  (testing "echo route"
    (let [response (app (mock/request :post "/echo" "Echo!"))]
      (is (= 200 (:status response)))
      (is (= "Echo!" (:body response))))

    (let [response (app (mock/request :put "/echo" "Hello!"))]
      (is (= 200 (:status response)))
      (is (= "Hello!" (:body response))))

    (let [response (app (mock/request :patch "/echo" "Goodbye!"))]
      (is (= 200 (:status response)))
      (is (= "Goodbye!" (:body response)))))

  (let [response (app (mock/request :post "/echo"))]
    (is (= 400 (:status response)))))

(deftest catchal-test
  (testing "when a handler throws an exception"
    (let [response (app (mock/request :get "/trouble"))]
      (testing "the status code is 500"
        (is (= 500 (:status response))))
      (testing "and the body only contains the exception message"
        (is (= "Divide by zero" (:body response)))))))


(deftest slurp-body-test
  (testing "when a handler requires a request body"
    (testing "and a body is provided"
      (let [response (body-echo-app (mock/request :post "/" "Echo!"))]
        (testing "the status code is 200"
          (is (= 200 (:status response)))
          (testing "with the request body in the response body"
            (is (= "Echo!" (:body response)))))))
    (testing "and a body is not provided"
      (let [response (body-echo-app (mock/request :get "/"))]
        (testing "the status code is 400"
          (is (= 400 (:status response))))))))


(deftest links-test
  (testing "the links/:id endpoint"
    (testing "when an id is provided"
      (let [response (app (mock/request :get "/links/foo123"))]
        (testing "returns a 200"
          (is (= 200 (:status response)))
          (testing "with the id in the body"
            (is (re-find #"foo123" (:body response)))))))
    (testing "when the id is omitted"
      (let [response (app (mock/request :get "/links"))]
        (testing "returns a 404"
          (is (= 404 (:status response))))))
    (testing "when the path is too long"
      (let [response (app (mock/request :get "/links/foo123/extra-segment"))]
        (testing "returns a 404"
          (is (= 404 (:status response))))))))


(deftest json-test
  (testing "the /clojurefy endpoint"
    (testing "when provided with some valid JSON"
      (let [example-map {"hello" "json"}
            example-json (json/encode example-map)
            response (app (-> (mock/request :post "/clojurefy" example-json)
            ;; note, we must set the content type of the request
                              (mock/content-type "application/json")))]
        (testing "returns a 200"
          (is (= 200 (:status response)))
          (testing "with a Clojure map in the body"
            (is (= (str example-map) (:body response)))))))
    (testing "when provided with invalid JSON"
      (let [response (app (-> (mock/request :post "/clojurefy" ";!:")
                              (mock/content-type "application/json")))]
        (testing "returns a 400"
          (is (= 400 (:status response))))))))


(deftest json-response-test
  (testing "the /info endpoint"
    (let [response (app (mock/request :get "/info"))]
      (testing "returns a 200"
        (is (= 200 (:status response))))
      (testing "with a valid JSON body"
        (let [info (json/decode (:body response))]
          (testing "containing the expected keys"
            (is (=  #{"Java Version" "OS Name" "OS Version"}
                    (set (keys info))))))))))