/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.adapter.dubbo;

import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.ResourceTypeConstants;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.adapter.dubbo.config.DubboConfig;
import com.alibaba.csp.sentinel.adapter.dubbo.fallback.DubboFallbackRegistry;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * <p>Apache Dubbo service provider filter that enables integration with Sentinel. Auto activated by default.</p>
 * <p>Note: this only works for Apache Dubbo 2.7.x or above version.</p>
 * <p>
 * If you want to disable the provider filter, you can configure:
 * <pre>
 * &lt;dubbo:provider filter="-sentinel.dubbo.provider.filter"/&gt;
 * </pre>
 *
 * @author Carpenter Lee
 * @author Eric Zhao
 */
@Activate(group = PROVIDER)
public class SentinelDubboProviderFilter extends BaseSentinelDubboFilter {

    public SentinelDubboProviderFilter() {
        RecordLog.info("Sentinel Apache Dubbo provider filter initialized");
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // Get origin caller.
        String application = DubboUtils.getApplication(invocation, "");
        String methodResourceName = DubboUtils.getResourceName(invoker, invocation, DubboConfig.getDubboConsumerPrefix());
        String interfaceResourceName = DubboUtils.getInterfaceName(invoker);
        try {
            // Only need to create entrance context at provider side, as context will take effect
            // at entrance of invocation chain only (for inbound traffic).
            ContextUtil.enter(methodResourceName, application);
            Entry interfaceEntry = SphU.asyncEntry(interfaceResourceName, ResourceTypeConstants.COMMON_RPC, EntryType.OUT);
            Entry methodEntry = SphU.asyncEntry(methodResourceName, ResourceTypeConstants.COMMON_RPC, EntryType.OUT, 1, invocation.getArguments());
            return wrapResult(invoker.invoke(invocation), interfaceEntry, methodEntry, true);
        } catch (BlockException e) {
            return DubboFallbackRegistry.getProviderFallback().handle(invoker, invocation, e);
        }
    }


}

