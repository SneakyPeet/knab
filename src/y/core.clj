(ns y.core
  (:require [y.api :as api]
            [clj-http.client :as http]))


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
         (filter #(false? :deleted %))
         (map #(select-keys % [:category :amount :date :payee_name])))))


(defn all-data [config]
  (let [{:keys [client]} config
        month (api/->data (api/parse-response (client (api/current-month))))
        categories (categories config month)
        transactions (transactions config month categories)]
    {:month (dissoc (:month month) :categories)
     :categories categories
     :transactions transactions}))


(comment

  (def d (all-data (get-config)))

  )
