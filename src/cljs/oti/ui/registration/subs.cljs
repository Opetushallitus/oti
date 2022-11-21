(ns oti.ui.registration.subs
  (:require [re-frame.core :as re-frame]
            [oti.routing :as routing]))

(def interesting-keys [:language :participant-data :registration-options])

(doseq [key interesting-keys]
  (re-frame/reg-sub
    key
    (fn [db _]
      (key db))))

(re-frame/reg-sub
  :continue-payment-link
  (fn [_ [_ data ui-lang]]
    (str (routing/p-a-route "/authenticated/register") "?registration-data=" (ajax.json/write-json-native data) "&ui-lang=" (name ui-lang))))