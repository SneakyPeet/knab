(ns y.server
  (:require [y.core :as core]
            [java-time :as time]
            [hiccup.core :as hiccup]
            [org.httpkit.server :as http]))


(defn- money [n]
  (-> n
      (/ 1000)
      int))


(defn main-page [d]
  (let [content
        [:div.columns
         [:div.column.is-narrow
          [:h1.title.has-text-right "Budget"]
          [:table.table.is-fullwidth
           [:tbody
            [:tr [:th "Budget"] [:td.has-text-right (money (:total-budgeted d))]]
            [:tr [:th "Spent"] [:td.has-text-right (money (:total-spent d))]]
            [:tr
             [:th {:style "border-top: solid 2px black"} "Left"]
             [:td.has-text-right
              {:style "border-top: solid 2px black"}
              (money (:total-left d))]]
            (when-not (zero? (:days-ahead-of-budget d))
              [:tr [:td.has-text-danger.has-text-right {:colspan 2}
                    (:days-ahead-of-budget d) " days ahead of budget!"]])]]]
         [:div.column.is-narrow
          [:h1.title.has-text-right "Available"]
          [:table.table.is-fullwidth
           [:thead
            [:tr [:th] [:th "Budgeted"] [:th "Actual"]]]
           [:tbody

            [:tr
             [:th "Available Per Day"]
             [:td.has-text-right (money (:budget-per-day d))]
             [:td.has-text-right (money (:available-per-day d))]]
            [:tr
             [:th "Available Per Week"]
             [:td.has-text-right (money (* 7 (:budget-per-day d)))]
             [:td.has-text-right (money (* 7 (:available-per-day d)))]]
            ]]]
         [:div.column.is-narrow
          [:h1.title.has-text-right "Category Spend"]
          [:table.table.is-fullwidth
           [:thead
            [:tr [:th "Category"] [:th "Spent"]]]
           [:tbody
            (->> (:categories d)
                 (filter #(neg? (:activity %)))
                 (sort-by :activity)
                 (map
                  (fn [{:keys [name activity]}]
                    [:tr [:td name] [:td.has-text-right (money (Math/abs activity))]])))]]]
         [:div.column
          [:h1.title.has-text-right "Transactions"]
          [:div {:style "overflow-x: scroll; width: 100%"}
           [:table.table.is-fullwidth
            [:thead
             [:tr [:th "When"] [:th "What"] [:th "Where"] [:th "Who"]]]
            [:tbody
             (->> (:transactions d)
                  (sort-by :date)
                  reverse
                  (map
                   (fn [{:keys [date category payee_name amount] :as t}]
                     [:tr
                      [:td (time/as (time/local-date date) :day-of-month)]
                      [:td.has-text-right (money amount)]
                      [:td (:name category)]
                      [:td payee_name]])))]]]]]]
    (hiccup/html
     [:html
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.7.4/css/bulma.min.css"}]]
      [:body
       [:section.section
        content]]])
    ))


(defn build-page []
  (let [data (core/process-data (core/all-data (core/get-config)))
        content (main-page data)]
    content))


(defn app [req]
  (try
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (build-page)}
    (catch Exception ex
      {:status 500
       :body (ex-cause ex)})))


(defonce ^:private *server* (atom nil))


(defn stop-server []
  (when-not (nil? @*server*)
    (@*server* :timeout 100)
    (reset! *server* nil)))


(defn start-server []
  (reset! *server* (http/run-server #'app {:port 8080})))


(defn -main [& args]
  (start-server))


(stop-server)
