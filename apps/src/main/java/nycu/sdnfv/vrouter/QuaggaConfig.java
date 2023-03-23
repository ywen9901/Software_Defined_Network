/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nycu.sdnfv.vrouter;

import java.util.List;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

public class QuaggaConfig extends Config<ApplicationId> {

  @Override
  public boolean isValid() {
    return hasField("quagga");
  }

  public String connectionPoint() {
    return get("quagga", null);
  }

  public String mac() {
    return get("quagga-mac", null);
  }

  public String vip() {
    return get("virtual-ip", null);
  }

  public String vmac() {
    return get("virtual-mac", null);
  }

  public List<String> peers() {
    return getList("peers", String::valueOf, null);
  }

}