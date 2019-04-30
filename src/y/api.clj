(ns y.api
  (:require [cheshire.core :as json]))


;;; Client

(defn wrap-token [request token]
  (assoc-in request [:headers "Authorization"] (str "Bearer " token)))


(defn wrap-endpoint [request budget-id]
  (-> request
      (update :url #(str "https://api.youneedabudget.com/v1/budgets/" budget-id %))
      (assoc :insecure? false)))


(defn wrap-json [request]
  (let [has-body? (contains? request :body)
        body-request? (contains? #{:post :put} (:method request))]
    (cond-> request
      true (assoc-in [:headers "Content-Type"] "application/json; charset=utf-8")
      (and body-request? has-body?) (update :body json/generate-string))))


(defn client
  [token budget-id http]
  (fn [request]
    (-> request
        (wrap-token token)
        (wrap-endpoint budget-id)
        wrap-json
        http)))


;;; Response

(defn parse-response [response]
  (cond-> response
    (string? (:body response))
    (update :body json/parse-string keyword)
    true
    (select-keys [:status :body :error])))


(defn response-error? [response]
  (contains? response :error))


(defn ->data [response]
  (get-in response [:body :data]))


;;; Api

(defn current-month []
  {:method :get
   :url "/months/current"})

(defn transactions-for-category [category-id since-date]
  {:method :get
   :url (str "/categories/" category-id "/transactions?since_date=" since-date)})
