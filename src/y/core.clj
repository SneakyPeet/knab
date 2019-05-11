(ns y.core
  (:require [y.api :as api]
            [clj-http.client :as http]
            [java-time :as time]))


(defn get-config
  ([] (get-config "config.edn"))
  ([path]
   (let [{:keys [budget-id token] :as config} (read-string (slurp path))
         client (api/client token budget-id http/request)]
     (assoc config :client client))))


(defn current-month [month-data]
  (get-in month-data [:month :month]))


(defn categories [config month-data]
  (let [{:keys [category-group-id uncategorized-group-id ]} config
        category-groups (->> month-data
                             :month
                             :categories
                             (group-by :category_group_id))]
    (->>
     (conj (get category-groups category-group-id)
           (->> (get category-groups uncategorized-group-id)
                (filter #(= "Uncategorized" (:name %)))
                first))
     (filter #(false? (:deleted %)))
     (filter #(false? (:hidden %)))
     (map #(select-keys % [:category_group_id :name :budgeted :activity :balance :id])))))


(defn transactions [config month-data categories]
  (let [client (:client config)
        since (current-month month-data)]
    (->> (for [category categories]
           (->> (api/->data
                 (api/parse-response
                  (client (api/transactions-for-category (:id category) since))))
                :transactions
                (map #(assoc % :category category))))
         (reduce into)
         (filter #(false? (:deleted %)))
         (map #(select-keys % [:category :amount :date :payee_name])))))


(defn all-data [config]
  (let [{:keys [client]} config
        month (api/->data (api/parse-response (client (api/current-month))))
        categories (categories config month)
        transactions (transactions config month categories)]
    {:month (dissoc (:month month) :categories)
     :categories categories
     :transactions transactions}))


(defn days-ahead-of-budget [budget-per-day total-left days-left]
  (loop [n 0]
    (let [days (- days-left n)]
      (if (<= days 0)
        n
        (let [budget (/ total-left days)]
          (if (>= budget budget-per-day)
            n
            (recur (inc n))))))))


(defn process-data [data]
  (let [{:keys [categories transactions]} data
        total-budgeted (->> categories
                            (map :budgeted)
                            (reduce +))
        total-spent (->> transactions
                         (map :amount)
                         (reduce +))
        total-left (+ total-budgeted total-spent)
        start-of-month (time/local-date "yyyy-MM-dd" (current-month data))
        days-in-month (time/as (time/minus (time/plus start-of-month (time/months 1)) (time/days 1)) :day-of-month)
        current-day (time/as (time/local-date) :day-of-month)
        days-left (- days-in-month (dec current-day))
        budget-per-day (/ total-budgeted days-in-month)
        available-per-day (/ total-left days-left)]
    (assoc data
           :total-budgeted total-budgeted
           :total-spent (Math/abs total-spent)
           :total-left total-left
           :days-in-month days-in-month
           :days-left days-left
           :budget-per-day budget-per-day
           :available-per-day available-per-day
           :days-ahead-of-budget (days-ahead-of-budget budget-per-day total-left days-left))))




(comment

  (def d (identity (all-data (get-config))))
  (process-data d)
  )
