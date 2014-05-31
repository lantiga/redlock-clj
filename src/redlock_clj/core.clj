(ns redlock-clj.core
  (:require [taoensso.carmine :as car :refer (wcar)]))

(def unlock-script 
  "if redis.call ('get',KEYS[1]) == ARGV [1] then
     return redis.call ('del',KEYS[1])
   else
     return 0
   end") 

(defn- quorum 
  [cluster-specs]
  (-> (count cluster-specs) (/ 2) (+ 1)))

(defn- lock-instance! 
  [server-specs resource value ttl]
  (try
    (car/wcar server-specs
      (car/set resource value :nx :px ttl))
    (catch Exception e false)))

(defn- unlock-instance! 
  [server-specs resource value]
  (try
    (car/wcar server-specs
      (car/eval unlock-script 1 resource value))
    (catch Exception e false)))

(defn- do-cluster
  [cluster-specs f & args]
  (doall 
    (pmap 
      (fn [[_ server-specs]] 
        (apply (partial f server-specs) args)) 
      cluster-specs)))

(defn- unique-lock-id []
  (str (java.util.UUID/randomUUID)))

(defn lock! [cluster-specs resource ttl & 
             {:keys [retry-count 
                     retry-delay 
                     clock-drift-factor] 
              :or {:retry-count 3
                   :retry-delay 200
                   :clock-drift-factor 0.01}}]
  (let [value (unique-lock-id)]
    (loop [retry 0] 
      (if (>= retry retry-count)
        false
        (let [start-time (System/currentTimeMillis)
              locked (do-cluster cluster-specs lock-instance! resource value ttl)
              n (count (filter identity locked))
              drift (-> ttl (* clock-drift-factor) (+ 2)) ;; TODO: check units 
              delta (- (System/currentTimeMillis) start-time)
              validity-time (-> ttl (- delta) (- drift))]
          (if (and (>= n (quorum cluster-specs)) (pos? validity-time)) 
            {:validity validity-time
             :resource resource
             :value value}
            (do
              (do-cluster cluster-specs unlock-instance! resource value)
              (Thread/sleep (rand retry-delay))
              (recur (inc retry)))))))))

(defn unlock! [cluster-specs lock]
  (do-cluster cluster-specs unlock-instance! (:resource lock) (:value lock)))

(comment
   
  (def cluster-specs
    {:server1 {:spec {:port 7777}} 
     :server2 {:spec {:port 7778}}
     :server3 {:spec {:port 7779}}})

  ) 
