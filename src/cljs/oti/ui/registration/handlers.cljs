(ns oti.ui.registration.handlers
  (:require [re-frame.core :as re-frame :refer [trim-v debug]]
            [ajax.core :as ajax]
            [oti.routing :as routing]
            [oti.spec :as os]
            [cognitect.transit :as transit]))

(re-frame/reg-event-fx
  :set-language
  (fn [{:keys [db]} [_ lang]]
    {:db         (assoc db :language lang)
     :http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/translations")
                  :params          {:lang (name lang)}
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :translations]
                  :on-failure      [:bad-response]}
     :loader true}))

(re-frame/reg-event-fx
  :load-participant-data
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/authenticated/participant-data")
                  :response-format (ajax/transit-response-format)
                  :params          {:lang "fi"}
                  :on-success      [:store-response-to-db :participant-data]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-available-sessions
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/exam-sessions")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :exam-sessions]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-registration-options
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/authenticated/registration-options")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :registration-options]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :store-registration
  (fn [_ [_ data ui-lang]]
    {:http-xhrio {:method          :post
                  :uri             (routing/p-a-route "/authenticated/register")
                  :params          {:registration-data data
                                    :ui-lang (name ui-lang)}
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:registration-saved]
                  :on-failure      [:registration-error]}}))

(re-frame/reg-event-fx
  :retry-payment
  (fn [_ [_ lang]]
    {:http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/authenticated/payment-form-data")
                  :params          {:lang (name lang)}
                  :response-format (ajax/transit-response-format)
                  :on-success      [:registration-saved]
                  :on-failure      [:registration-error]}}))

(re-frame/reg-event-fx
  :registration-saved
  [trim-v]
  (fn [{:keys [db]} [response]]
    (if-let [payment-form-data (:oti.spec/payment-form-data response)]
      {:submit-payment-form payment-form-data}
      {:db (update db :participant-data merge {:registration-status :success
                                               :registration-message (:registration-message response)})})))

(re-frame/reg-event-db
  :registration-error
  [trim-v]
  (fn
    [db [{:keys [response]}]]
    (update db :participant-data merge {:registration-status :error
                                        :registration-message (or (:registration-message response)
                                                                  "Ilmoittautumisessa tapahtui odottamaton virhe")})))

(re-frame/reg-fx
  :submit-payment-form
  (fn [{::os/keys [uri payment-form-params]}]
    (let [form (doto (.createElement js/document "form")
                 (.setAttribute "method" "post")
                 (.setAttribute "action" uri)
                 (.setAttribute "accept-charset" "ISO-8859-1"))]
      ;; For IE compatibility
      (-> js/document .-charset (set! "ISO-8859-1"))
      (doseq [[key val] payment-form-params]
        (->> (doto (.createElement js/document "input")
               (.setAttribute "type" "hidden")
               (.setAttribute "name" (name key))
               (.setAttribute "value" (str (if (transit/bigdec? val)
                                             (.-rep val)
                                             val))))
             (.appendChild form)))
      (-> js/document .-body (.appendChild form))
      (.submit form))))
