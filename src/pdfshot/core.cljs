(ns pdfshot.core
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]))

(def fs (nodejs/require "fs"))
(def puppeteer (nodejs/require "puppeteer"))
(def express (nodejs/require "express"))

(defn- on-sent
  [res temp-path err]
  (.unlink fs temp-path (fn [_]))
  (when err
    (-> res
        (.status 500)
        (.send "An error occurred."))))

(defn- pdf
  [req res no-sandbox]
  (let [target (.. req -query -target)
        wait-for (.. req -query -wait_for)
        temp-path (str "print/" (.toString (random-uuid)) ".pdf")
        file-name "print.pdf"]
    (go
      (let [browser (<p! (.launch puppeteer
                                  #js{:headless true
                                      :executablePath "/usr/bin/chromium-browser"
                                      :args (if no-sandbox
                                              #js["--disable-dev-shm-usage" "--no-sandbox"]
                                              #js["--disable-dev-shm-usage"])}))
            page (<p! (.newPage browser))]
        (try
          (<p! (.goto page target))
          (when wait-for (<p! (.waitForSelector page wait-for #js{:timeout 60000})))
          (<p! (.pdf page #js{:path temp-path
                              :format "A4"}))
          (catch js/Error e
            (.error js/console e))
          (finally
            (.close browser)
            (.download res temp-path file-name (partial on-sent res temp-path))))))))

(defn -main
  [& args]
  (let [port (or (some-> js/process .-env .-PDFSHOT_PORT (js/parseInt 10)) 8000)
        no-sandbox (= "--no-sandbox" (first args))]
    (doto (express)
      (.get "/print.pdf" (fn [req res] (pdf req res no-sandbox)))
      (.listen port #(.log js/console (str "Server started on port " port))))))

(set! *main-cli-fn* -main)
