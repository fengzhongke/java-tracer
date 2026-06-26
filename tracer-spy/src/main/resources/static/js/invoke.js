/**
 * invoke.js ? Simplified chain builder for cascade method/field calls.
 *
 * Data model: flat array chainSteps[] instead of target-linked tree.
 * Convert to/from target-linked tree only for backend API serialization.
 * Sub-chain params use nested subSteps[] arrays (recursive nesting allowed).
 *
 * Path format: "stepIdx:paramIdx" for main chain params.
 * Nested: "stepIdx:paramIdx:subStepIdx:subParamIdx:deeperStepIdx:deeperParamIdx..."
 * Even indices = step index, odd indices = param index within that step's params.
 * The path always ends at a param (odd number of colon-separated parts).
 */

// =============================================
// Model
// =============================================

var chainSteps = [];  // Flat array of steps. [0]=first, [1]=second, etc.
var maxDisplayLen = 500;  // Max display length for exceptions, return values (configurable via input)

function createMethodStep(className, methodName, paramTypes, isStatic, loaderId) {
    return {
        callType: 'method',
        className: className || '',
        methodName: methodName || '',
        paramTypes: paramTypes || '',  // comma-separated string
        params: [],
        isStatic: isStatic !== undefined ? isStatic : true,
        loaderId: loaderId || 0,
        returnType: '',
        returnValue: '',
        isVoid: false,
        exception: null,
        duration: 0,
        _returnTypeHint: ''
    };
}

function createFieldStep(className, fieldName, isStatic, loaderId) {
    return {
        callType: 'field',
        className: className || '',
        methodName: fieldName || '',
        paramTypes: '',
        params: [],
        isStatic: isStatic !== undefined ? isStatic : false,
        loaderId: loaderId || 0,
        returnType: '',
        returnValue: '',
        isVoid: false,
        exception: null,
        duration: 0,
        _returnTypeHint: ''
    };
}

function createArrayGetStep(index, loaderId) {
    return {
        callType: 'arrayGet',
        className: '',
        methodName: '',
        index: index || 0,
        paramTypes: '',
        params: [],
        isStatic: false,
        loaderId: loaderId || 0,
        returnType: '',
        returnValue: '',
        isVoid: false,
        exception: null,
        duration: 0,
        _returnTypeHint: ''
    };
}

function createClassRefStep(className, loaderId) {
    return {
        callType: 'classRef',
        className: className || '',
        methodName: '',
        paramTypes: '',
        params: [],
        isStatic: true,
        loaderId: loaderId || 0,
        returnType: 'Class',
        returnValue: '',
        isVoid: false,
        exception: null,
        duration: 0,
        _returnTypeHint: 'Class'
    };
}

function createCapturedRefStep(className, capturedValue, capturedParamIndex) {
    return {
        callType: 'capturedRef',
        className: className || '',
        methodName: '',
        capturedValue: capturedValue || '',
        capturedParamIndex: capturedParamIndex >= 0 ? capturedParamIndex : 0,
        paramTypes: '',
        params: [],
        isStatic: false,
        loaderId: 0,
        returnType: '',
        returnValue: '',
        isVoid: false,
        exception: null,
        duration: 0,
        _returnTypeHint: ''
    };
}

function createLiteralParam(value, valueType) {
    return {
        callType: 'literal',
        value: value || '',
        valueType: valueType || 'String',
        _paramTypeHint: valueType || 'String'
    };
}

function createNullParam(typeHint) {
    return {
        callType: 'null',
        _paramTypeHint: typeHint || ''
    };
}

function createClassRefParam(className, loaderId) {
    return {
        callType: 'classRef',
        className: className || '',
        loaderId: loaderId || 0,
        _paramTypeHint: 'Class'
    };
}

function createSubchainParam(typeHint) {
    return {
        callType: 'subchain',
        subSteps: [],
        _paramTypeHint: typeHint || ''
    };
}

function createAutoParam(typeName) {
    var PRIMS = ['int','long','double','float','boolean','short','byte','char'];
    if (PRIMS.indexOf(typeName) >= 0) return createLiteralParam('', typeName);
    if (typeName === 'String') return createLiteralParam('', 'String');
    return createNullParam(typeName);
}

// =============================================
// Utilities
// =============================================

function shortClassName(fqn) {
    if (!fqn) return '?';
    // Handle inner classes: com.example.Outer$Inner → "Inner"
    // Take part after last '$' if present, otherwise part after last '.'
    var idx = fqn.lastIndexOf('$');
    if (idx >= 0) return fqn.substring(idx + 1);
    idx = fqn.lastIndexOf('.');
    return idx >= 0 ? fqn.substring(idx + 1) : fqn;
}

function truncate(s, max) {
    if (!s) return '';
    return s.length <= max ? s : s.substring(0, max) + '...';
}

function updateMaxDisplayLen(val) {
    var n = parseInt(val);
    if (n && n >= 60) {
        maxDisplayLen = n;
        // Sync to backend so return values are not truncated
        $.post('/invoke/config-max-len.json', {maxLen: String(n)});
        // Re-render chain if there are steps with results
        if (chainSteps.length > 0) {
            renderChain();
            if ($('#invoke_result').is(':visible')) {
                showChainResult();
            }
        }
    }
}

/** Check if an exception message is a cascade (target invocation failed) rather than a root cause. */
function isCascadeException(exception) {
    return exception && exception.indexOf('target invocation failed:') === 0;
}

/** Extract the root cause from a cascade exception chain.
 *  "target invocation failed: target invocation failed: class not found" → "class not found"
 *  Returns the deepest non-cascade error message. */
function extractRootCause(exception) {
    if (!exception) return '';
    var msg = exception;
    while (msg.indexOf('target invocation failed:') === 0) {
        msg = msg.substring('target invocation failed:'.length).trim();
    }
    return msg;
}

function escapeHtml(s) {
    if (!s) return '';
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function escapeAttr(s) {
    if (!s) return '';
    return String(s).replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/'/g,'&#39;').replace(/</g,'&lt;');
}

function isPrimitiveType(typeName) {
    if (!typeName) return true;
    var prims = ['void','boolean','byte','short','int','long','float','double','char'];
    return prims.indexOf(typeName) >= 0;
}

function canChainFrom(typeName) {
    if (!typeName) return false;
    var noChain = ['void','boolean','byte','short','int','long','float','double','char','String'];
    return noChain.indexOf(typeName) < 0;
}

function isArrayOrListType(typeName) {
    if (!typeName) return false;
    return typeName.endsWith('[]') || typeName === 'List' || typeName === 'ArrayList' ||
        typeName === 'Collection' || typeName === 'Set' || typeName === 'Map' ||
        typeName.startsWith('List<') || typeName.startsWith('Collection<') ||
        typeName.startsWith('Set<');
}

/** Get the element type from an array type name.
 *  "String[]" → "String", "int[]" → "int", "Object[]" → "Object"
 */
function elementTypeName(typeName) {
    if (!typeName) return 'Object';
    if (typeName.endsWith('[]')) return typeName.substring(0, typeName.length - 2);
    return 'Object';  // List/Collection types → element is Object (runtime resolves)
}

function canUseSubchain(typeName) {
    if (!typeName) return false;
    var prims = ['void','int','long','double','float','boolean','short','byte','char','String'];
    return prims.indexOf(typeName) < 0;
    return prims.indexOf(typeName) < 0;
}

function extractClassNameFromReturnValue(returnValue) {
    if (!returnValue) return null;
    var match = returnValue.match(/^([a-zA-Z_][a-zA-Z0-9_.]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)+)@/);
    if (match) return match[1];
    match = returnValue.match(/^([a-zA-Z_][a-zA-Z0-9_.]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)+)$/);
    if (match) return match[1];
    return null;
}

/** Normalize paramTypes to a comma-separated string.
 *  Backend may return it as an array ["String","int"], our model stores it as "String,int". */
function normalizeParamTypes(paramTypes) {
    if (!paramTypes) return '';
    if (typeof paramTypes === 'string') return paramTypes;
    if (Array.isArray(paramTypes)) return paramTypes.join(',');
    return String(paramTypes);
}

function getParamTypeHint(step, paramIndex) {
    var pt = normalizeParamTypes(step.paramTypes);
    if (!pt || !pt.trim()) return null;
    var parts = pt.split(',');
    return paramIndex < parts.length ? parts[paramIndex].trim() : null;
}

/** Clear all result data from steps and sub-chains (returnType, returnValue, exception, duration).
 *  Called whenever the chain structure changes so stale errors don't mislead the user,
 *  and before each invocation to reset the display state. */
function clearAllResults() {
    clearStepsResults(chainSteps);
}

function clearStepsResults(steps) {
    if (!steps) return;
    for (var i = 0; i < steps.length; i++) {
        steps[i].returnType = '';
        steps[i].returnValue = '';
        steps[i].isVoid = false;
        steps[i].exception = null;
        steps[i].arrayLength = -1;
        steps[i].duration = 0;
        // Also clear sub-chain param results
        if (steps[i].params) {
            for (var p = 0; p < steps[i].params.length; p++) {
                var param = steps[i].params[p];
                if (param.callType === 'subchain' && param.subSteps) {
                    clearStepsResults(param.subSteps);
                } else if (param.callType === 'literal') {
                    param.returnType = '';
                    param.returnValue = '';
                }
            }
        }
    }
}

// =============================================
// Path-based navigation
// =============================================
// Path format: colon-separated integers, alternating stepIdx and paramIdx.
// "0:2"       ? chainSteps[0].params[2]
// "0:2:1:0"   ? chainSteps[0].params[2].subSteps[1].params[0]
// Always has an odd number of parts (path always ends at a param).

/** Navigate to a param using its full path string. Returns the param object. */
function getParamByPath(pathStr) {
    var parts = pathStr.split(':');
    // All but the last 2 parts navigate through sub-chain nesting
    var steps = chainSteps;
    for (var i = 0; i < parts.length - 2; i += 2) {
        var stepIdx = parseInt(parts[i]);
        var paramIdx = parseInt(parts[i + 1]);
        var step = steps[stepIdx];
        if (!step || !step.params || paramIdx >= step.params.length) return null;
        var param = step.params[paramIdx];
        if (param.callType !== 'subchain' || !param.subSteps) return null;
        steps = param.subSteps;
    }
    // Final 2 parts point to the target param
    var finalStepIdx = parseInt(parts[parts.length - 2]);
    var finalParamIdx = parseInt(parts[parts.length - 1]);
    var finalStep = steps[finalStepIdx];
    if (!finalStep || !finalStep.params || finalParamIdx >= finalStep.params.length) return null;
    return finalStep.params[finalParamIdx];
}

/** Navigate to the step that owns a param using its path string. */
function getStepByPath(pathStr) {
    var parts = pathStr.split(':');
    var steps = chainSteps;
    for (var i = 0; i < parts.length - 2; i += 2) {
        var stepIdx = parseInt(parts[i]);
        var paramIdx = parseInt(parts[i + 1]);
        var step = steps[stepIdx];
        if (!step || !step.params || paramIdx >= step.params.length) return null;
        var param = step.params[paramIdx];
        if (param.callType !== 'subchain' || !param.subSteps) return null;
        steps = param.subSteps;
    }
    var finalStepIdx = parseInt(parts[parts.length - 2]);
    return steps[finalStepIdx] || null;
}

/** Get the subSteps array for a sub-chain param identified by its path prefix.
 *  pathPrefix is all parts except the last 2 (e.g., "0:2" for chainSteps[0].params[2].subSteps).
 *  For main chain, pathPrefix = "" (empty string). */
function getSubStepsByPathPrefix(pathPrefix) {
    if (!pathPrefix) return chainSteps;
    var parts = pathPrefix.split(':');
    var steps = chainSteps;
    for (var i = 0; i < parts.length; i += 2) {
        var stepIdx = parseInt(parts[i]);
        var paramIdx = parseInt(parts[i + 1]);
        var step = steps[stepIdx];
        if (!step || !step.params || paramIdx >= step.params.length) return null;
        var param = step.params[paramIdx];
        if (param.callType !== 'subchain' || !param.subSteps) return null;
        steps = param.subSteps;
    }
    return steps;
}

/** Replace a param at a given path with a new param object. */
function replaceParamByPath(pathStr, newParam) {
    var parts = pathStr.split(':');
    var steps = chainSteps;
    for (var i = 0; i < parts.length - 2; i += 2) {
        var stepIdx = parseInt(parts[i]);
        var paramIdx = parseInt(parts[i + 1]);
        var step = steps[stepIdx];
        if (!step || !step.params || paramIdx >= step.params.length) return false;
        var param = step.params[paramIdx];
        if (param.callType !== 'subchain' || !param.subSteps) return false;
        steps = param.subSteps;
    }
    var finalStepIdx = parseInt(parts[parts.length - 2]);
    var finalParamIdx = parseInt(parts[parts.length - 1]);
    var finalStep = steps[finalStepIdx];
    if (!finalStep || !finalStep.params || finalParamIdx >= finalStep.params.length) return false;
    // Preserve _paramTypeHint from the old param
    newParam._paramTypeHint = finalStep.params[finalParamIdx]._paramTypeHint || getParamTypeHint(finalStep, finalParamIdx) || '';
    finalStep.params[finalParamIdx] = newParam;
    return true;
}

// =============================================
// Rendering
// =============================================

var expandedEditors = {};  // Map of pathStr ? true

function toggleParamEditor(pathStr) {
    if (expandedEditors[pathStr]) {
        delete expandedEditors[pathStr];
    } else {
        expandedEditors[pathStr] = true;
    }
    renderChain();
}

/** Toggle chain param: if currently not subchain, switch to subchain and expand;
 *  if already subchain and expanded, collapse; if subchain but collapsed, expand. */
function toggleChainParam(paramPath) {
    var param = getParamByPath(paramPath);
    if (!param) return;
    if (param.callType === 'subchain') {
        // Already subchain — toggle expand/collapse
        if (expandedEditors[paramPath]) {
            delete expandedEditors[paramPath];
        } else {
            expandedEditors[paramPath] = true;
        }
    } else if (param.callType === 'classRef') {
        // Convert classRef into subchain with classRef as first step
        var subSteps = [createClassRefStep(param.className, param.loaderId)];
        var step = getStepByPath(paramPath);
        var paramIdx = parseInt(paramPath.split(':').pop());
        var typeHint = param._paramTypeHint || (step ? getParamTypeHint(step, paramIdx) : '') || '';
        replaceParamByPath(paramPath, createSubchainParam(typeHint));
        // Set subSteps on the newly created subchain param
        var newParam = getParamByPath(paramPath);
        newParam.subSteps = subSteps;
        expandedEditors[paramPath] = true;
    } else {
        // null / literal / other — switch to empty subchain
        var step = getStepByPath(paramPath);
        var paramIdx = parseInt(paramPath.split(':').pop());
        var typeHint = param._paramTypeHint || (step ? getParamTypeHint(step, paramIdx) : '') || '';
        replaceParamByPath(paramPath, createSubchainParam(typeHint));
        expandedEditors[paramPath] = true;
    }
    clearAllResults();
    renderChain();
}

function renderChain() {
    if (chainSteps.length === 0) {
        $('#chain_container').html('');
        $('#chain_empty_state').show();
        return;
    }
    $('#chain_empty_state').hide();

    var html = '';
    for (var i = 0; i < chainSteps.length; i++) {
        html += renderStep(chainSteps[i], i, '');
    }

    $('#chain_container').html(html);
}

function renderStep(step, index, parentPathPrefix) {
    // parentPathPrefix: "" for main chain, "0:2" for subchain inside chainSteps[0].params[2]
    // The full path prefix for this step's params would be parentPathPrefix + ":" + index
    // But parentPathPrefix already includes this step's index in the path for its params.
    // Actually, we need a different scheme. Let me think...
    // For main chain step i, its params have path "i:paramIdx"
    // For sub-chain step j inside parent param "0:2", its params have path "0:2:j:paramIdx"
    // parentPathPrefix tells us what to prepend before the step's own param paths.

    var pathPrefix = parentPathPrefix ? (parentPathPrefix + ':' + index) : String(index);

    var cssClass = 'chain-step';
    if (step.callType === 'field') cssClass += ' step-field';
    else if (step.callType === 'classRef') cssClass += ' step-classref';
    else if (step.callType === 'capturedRef') cssClass += ' step-capturedref';
    else if (step.callType === 'arrayGet') cssClass += ' step-arrayget';
    else if (step.isStatic) cssClass += ' step-static';
    else cssClass += ' step-method';

    var html = '<div class="' + cssClass + '">';

    // Step number (only for main chain)
    if (!parentPathPrefix) {
        html += '<span class="step-num">' + (index + 1) + '</span>';
    }

    html += '<div class="step-content">';

    // Header — type badge is the invoke button
    html += '<div class="step-header">';
    if (!parentPathPrefix) {
        // Main chain — invoke up to this step
        if (step.callType === 'field') {
            html += '<button class="type-badge badge-field" onclick="invokeChainUpTo(' + index + ')" title="Invoke up to here">field</button>';
            if (step.isStatic) html += '<span class="type-badge badge-static">static</span>';
        } else if (step.callType === 'classRef') {
            html += '<button class="type-badge badge-classref" onclick="invokeChainUpTo(' + index + ')" title="Invoke up to here">Class</button>';
        } else if (step.callType === 'capturedRef') {
            html += '<button class="type-badge badge-capturedref" onclick="invokeChainUpTo(' + index + ')" title="Invoke up to here">captured</button>';
        } else if (step.callType === 'arrayGet') {
            html += '<button class="type-badge badge-arrayget" onclick="invokeChainUpTo(' + index + ')" title="Invoke up to here">[' + step.index + ']</button>';
        } else {
            html += '<button class="type-badge badge-method" onclick="invokeChainUpTo(' + index + ')" title="Invoke up to here">method</button>';
            if (step.isStatic) html += '<span class="type-badge badge-static">static</span>';
        }
    } else {
        // Sub-chain — invoke this sub-chain
        if (step.callType === 'field') {
            html += '<button class="type-badge badge-field" onclick="invokeSubChain(\'' + parentPathPrefix + '\')" title="Invoke sub-chain">field</button>';
            if (step.isStatic) html += '<span class="type-badge badge-static">static</span>';
        } else if (step.callType === 'classRef') {
            html += '<button class="type-badge badge-classref" onclick="invokeSubChain(\'' + parentPathPrefix + '\')" title="Invoke sub-chain">Class</button>';
        } else if (step.callType === 'capturedRef') {
            html += '<button class="type-badge badge-capturedref" onclick="invokeSubChain(\'' + parentPathPrefix + '\')" title="Invoke sub-chain">captured</button>';
        } else if (step.callType === 'arrayGet') {
            html += '<button class="type-badge badge-arrayget" onclick="invokeSubChain(\'' + parentPathPrefix + '\')" title="Invoke sub-chain">[' + step.index + ']</button>';
        } else {
            html += '<button class="type-badge badge-method" onclick="invokeSubChain(\'' + parentPathPrefix + '\')" title="Invoke sub-chain">method</button>';
            if (step.isStatic) html += '<span class="type-badge badge-static">static</span>';
        }
    }
    html += '<span class="step-name">';
    if (step.callType === 'classRef') {
        html += escapeHtml(shortClassName(step.className)) + '.class';
    } else if (step.callType === 'capturedRef') {
        html += '<span style="color:#f0ad4e;font-weight:bold;">this</span> ';
        html += '<span style="color:#999;font-size:11px;">(' + escapeHtml(shortClassName(step.className)) + ': ' + escapeHtml(truncate(step.capturedValue, 40)) + ')</span>';
    } else if (step.callType === 'arrayGet') {
        html += '[' + step.index + ']';
    } else if (step.className) {
        html += escapeHtml(shortClassName(step.className)) + '.' + escapeHtml(step.methodName || '');
    } else {
        html += escapeHtml(step.methodName || '?');
    }
    // Inline result after the name on the same line
    if (step.exception) {
        if (isCascadeException(step.exception)) {
            html += ' <span class="ret-cascade">=> cascade: ' + escapeHtml(truncate(extractRootCause(step.exception), maxDisplayLen)) + '</span>';
        } else {
            html += ' <span class="ret-error">=> ' + escapeHtml(truncate(step.exception, maxDisplayLen)) + '</span>';
        }
    } else if (step.returnType || step.returnValue) {
        html += ' => <span class="ret-type">' + escapeHtml(step.returnType) + '</span>';
        if (step.returnValue) html += ': <span class="ret-value">' + escapeHtml(truncate(step.returnValue, maxDisplayLen)) + '</span>';
        if (step.arrayLength >= 0) html += ' <span class="ret-length">[length=' + step.arrayLength + ']</span>';
        if (step.duration > 0) html += ' <span class="ret-duration">(' + step.duration + 'ms)</span>';
    }
    html += '</span>';
    html += '<button class="btn btn-danger btn-xs pull-right" onclick="removeStepAt(\'' + pathPrefix + '\')" title="Remove"><span class="glyphicon glyphicon-remove"></span></button>';
    html += '</div>';

    // Params (for method calls)
    if (step.callType === 'method' && step.params && step.params.length > 0) {
        html += '<div class="step-params">';
        for (var p = 0; p < step.params.length; p++) {
            if (p > 0) html += '<span class="param-sep">,</span>';
            var paramPath = pathPrefix + ':' + p;
            html += renderParamInline(step, p, paramPath);
        }
        html += '</div>';
    }

    // Sub-chain panels (toggled by chain button)
    if (step.callType === 'method' && step.params && step.params.length > 0) {
        for (var p = 0; p < step.params.length; p++) {
            var paramPath2 = pathPrefix + ':' + p;
            var param2 = step.params[p];
            if (param2.callType === 'subchain' && expandedEditors[paramPath2]) {
                html += renderSubchain(param2, paramPath2);
            }
        }
    }

    // Actions — last step has Add Next
    html += '<div class="step-actions">';
    if (!parentPathPrefix) {
        var stepsArr = chainSteps;
        if (stepsArr && index === stepsArr.length - 1) {
            var retHint = step._returnTypeHint || step.returnType || '';
            // Show [i] button when return type is array or List
            if (isArrayOrListType(retHint)) {
                html += '<button class="btn btn-success btn-xs" onclick="addArrayGetStep()"><span class="glyphicon glyphicon-th-list"></span> [i]</button>';
            }
            if (canChainFrom(retHint)) {
                html += '<button class="btn btn-primary btn-xs" onclick="addNextStep()"><span class="glyphicon glyphicon-plus"></span> Next Step</button>';
            }
        }
    } else {
        var subStepsArr = getSubStepsByPathPrefix(parentPathPrefix);
        if (subStepsArr && index === subStepsArr.length - 1) {
            var retHint = step._returnTypeHint || step.returnType || '';
            // Show [i] button when return type is array or List
            if (isArrayOrListType(retHint)) {
                html += '<button class="btn btn-success btn-xs" onclick="addArrayGetSubStep(\'' + parentPathPrefix + '\')"><span class="glyphicon glyphicon-th-list"></span> [i]</button>';
            }
            if (canChainFrom(retHint)) {
                html += '<button class="btn btn-primary btn-xs" onclick="addNextSubStep(\'' + parentPathPrefix + '\')"><span class="glyphicon glyphicon-plus"></span> Next</button>';
            }
        }
    }
    html += '</div>';

    html += '</div>'; // step-content
    html += '</div>'; // chain-step
    return html;
}

function renderParamInline(step, paramIndex, paramPath) {
    var param = step.params[paramIndex];
    var typeHint = param._paramTypeHint || getParamTypeHint(step, paramIndex) || '';
    var isPrim = isPrimitiveType(typeHint) && typeHint !== 'void';
    var canSub = canUseSubchain(typeHint);

    var html = '<span class="param-inline">';
    html += '<span class="param-type-label">' + escapeHtml(shortClassName(typeHint)) + '</span>';

    // Mode tabs — always show null; value for prim/string; chain for object types
    html += '<span class="param-tab-null' + (param.callType === 'null' ? ' active' : '') + '" onclick="switchParamMode(\'' + paramPath + '\',\'null\')">null</span>';
    if (isPrim || typeHint === 'String') {
        html += '<span class="param-tab-value' + (param.callType === 'literal' ? ' active' : '') + '" onclick="switchParamMode(\'' + paramPath + '\',\'literal\')">value</span>';
    }
    if (canSub) {
        html += '<span class="param-tab-chain' + (param.callType === 'subchain' ? ' active' : '') + '" onclick="toggleChainParam(\'' + paramPath + '\')">chain</span>';
    }

    // Show current value inline
    if (param.callType === 'literal') {
        html += ' <input type="text" class="form-control input-sm param-value-input" value="' + escapeAttr(param.value) + '" onchange="updateParamValue(\'' + paramPath + '\',this.value)" placeholder="' + escapeHtml(param.valueType) + ' value">';
    } else if (param.callType === 'classRef') {
        html += ' <span class="param-val-classref">' + escapeHtml(shortClassName(param.className)) + '.class</span>';
    } else if (param.callType === 'subchain') {
        // No inline text — sub-chain panel below shows the detail
    }

    html += '</span>';
    return html;
}

function renderParamEditor(step, paramIndex, paramPath) {
    var param = step.params[paramIndex];
    var typeHint = param._paramTypeHint || getParamTypeHint(step, paramIndex) || '';
    var html = '';

    // Mode selector buttons
    html += '<div class="param-editor-row">';
    html += '<span class="param-type-label" style="min-width:60px;">' + escapeHtml(typeHint) + '</span>';

    var isPrim = isPrimitiveType(typeHint) && typeHint !== 'void';
    var canSub = canUseSubchain(typeHint);

    if (isPrim || typeHint === 'String') {
        html += '<button class="btn btn-xs ' + (param.callType === 'literal' ? 'btn-success' : 'btn-default') + '" onclick="switchParamMode(\'' + paramPath + '\',\'literal\')">value</button>';
    }
    html += '<button class="btn btn-xs ' + (param.callType === 'null' ? 'btn-primary' : 'btn-default') + '" onclick="switchParamMode(\'' + paramPath + '\',\'null\')">null</button>';
    if (canSub) {
        html += '<button class="btn btn-xs ' + (param.callType === 'subchain' ? 'btn-warning' : 'btn-default') + '" onclick="switchParamMode(\'' + paramPath + '\',\'subchain\')">chain</button>';
    }

    html += '</div>';

    // Mode-specific content
    if (param.callType === 'literal') {
        html += '<div class="param-editor-row" style="margin-top:4px;">';
        html += '<input type="text" class="form-control input-sm" style="width:150px;" value="' + escapeAttr(param.value) + '" onchange="updateParamValue(\'' + paramPath + '\',this.value)" placeholder="' + escapeHtml(param.valueType) + ' value">';
        html += '</div>';
    } else if (param.callType === 'null') {
        html += '<div style="color:#999;font-size:12px;margin-top:4px;">Passes null</div>';
    } else if (param.callType === 'subchain') {
        html += renderSubchain(param, paramPath);
    }

    return html;
}

function renderSubchain(param, paramPath) {
    // paramPath points to this sub-chain param itself.
    // The sub-chain's steps are param.subSteps.
    // Each sub-step's params have paths: paramPath + ":subIdx:paramIdx"
    // The path prefix for sub-step rendering is paramPath (which is the path to the subchain param).
    // Sub-step j's path for removal is paramPath + ":" + j

    var subSteps = param.subSteps || [];
    var nestingLevel = paramPath.split(':').length / 2;  // How deep we are (1 = main chain, 2 = first sub, etc.)
    var depthClass = '';
    if (nestingLevel >= 2 && nestingLevel <= 4) depthClass = ' depth-' + nestingLevel;

    var html = '<div class="sub-chain' + depthClass + '">';

    if (subSteps.length === 0) {
        html += '<div style="color:#999;font-size:12px;padding:4px;">Empty chain ? add a step below</div>';
    }

    for (var i = 0; i < subSteps.length; i++) {
        // Each sub-step uses paramPath as the parentPathPrefix
        html += renderStep(subSteps[i], i, paramPath);
        // if (i < subSteps.length - 1) {
        //     html += '<div class="chain-arrow">?</div>';
        // }
    }

    // Add first step to empty sub-chain
    if (subSteps.length === 0) {
        html += '<div style="text-align:center;padding:6px;">';
        html += '<button class="btn btn-primary btn-xs" onclick="addSubStep(\'' + paramPath + '\')"><span class="glyphicon glyphicon-plus"></span> Add First Step</button>';
        html += '</div>';
    }

    html += '</div>';
    return html;
}

// =============================================
// Chain manipulation
// =============================================

function addFirstStep() {
    msContext = { action: 'addFirstStep' };
    showMemberSelector();
}

function addNextStep() {
    if (chainSteps.length === 0) {
        addFirstStep();
        return;
    }
    var lastStep = chainSteps[chainSteps.length - 1];
    var realType = lastStep.realReturnType || '';

    // If we have the actual runtime class name from a previous invocation,
    // directly load its members — no need to re-execute the chain.
    if (realType && canChainFrom(shortClassName(realType))) {
        msContext = { action: 'addNextStep', returnTypeHint: shortClassName(realType), currentClassName: realType, currentLoaderId: lastStep.loaderId || 0 };
        loadMembersForModal(realType, lastStep.loaderId || 0);
    } else {
        // No realReturnType available (chain not executed yet) — peek first
        invokeForPeek();
    }
}

function addArrayGetStep() {
    if (chainSteps.length === 0) {
        alert('Add at least one step first');
        return;
    }
    var lastStep = chainSteps[chainSteps.length - 1];
    var retHint = lastStep._returnTypeHint || lastStep.returnType || '';

    // If return type is unknown, invoke first to discover the actual type
    if (!retHint || retHint === 'Object' || !isArrayOrListType(retHint)) {
        msContext = { action: 'addNextStepAfterPeek' };
        invokeForPeek();
        // After peek, if the actual type turns out to be array/list, add arrayGet step
        // This is handled in the peek success callback below
        return;
    }

    var indexStr = prompt('Enter array/list index (0, 1, 2, ...):');
    if (indexStr === null || indexStr === '') return;  // user cancelled
    var idx = parseInt(indexStr);
    if (isNaN(idx) || idx < 0) { alert('Invalid index, must be a non-negative integer'); return; }

    var newNode = createArrayGetStep(idx);
    newNode._returnTypeHint = elementTypeName(retHint);
    newNode.className = '';  // resolved from target runtime type
    newNode.isStatic = false;
    chainSteps.push(newNode);
    clearAllResults();
    renderChain();
}

function addArrayGetSubStep(pathPrefix) {
    var subSteps = getSubStepsByPathPrefix(pathPrefix);
    if (!subSteps || subSteps.length === 0) {
        alert('Add at least one sub-step first');
        return;
    }
    var lastSub = subSteps[subSteps.length - 1];
    var retHint = lastSub._returnTypeHint || lastSub.returnType || '';

    if (!retHint || !isArrayOrListType(retHint)) {
        msContext = { action: 'addNextSubStepAfterPeek', pathStr: pathPrefix };
        invokeForPeekSub(pathPrefix);
        return;
    }

    var indexStr = prompt('Enter array/list index (0, 1, 2, ...):');
    if (indexStr === null || indexStr === '') return;
    var idx = parseInt(indexStr);
    if (isNaN(idx) || idx < 0) { alert('Invalid index, must be a non-negative integer'); return; }

    var newNode = createArrayGetStep(idx);
    newNode._returnTypeHint = elementTypeName(retHint);
    newNode.className = '';
    newNode.isStatic = false;
    subSteps.push(newNode);
    expandedEditors[pathPrefix] = true;
    clearAllResults();
    renderChain();
}

/**
 * Remove a step at the given path.
 * pathPrefix for main chain step i = "i" (just the step index)
 * pathPrefix for sub-chain step j inside param "0:2" = "0:2:j"
 */
function removeStepAt(pathPrefix) {
    // Parse the pathPrefix to find which steps array and index
    var parts = pathPrefix.split(':');
    var steps = chainSteps;
    // Navigate through sub-chains to reach the target steps array
    // The pathPrefix format alternates: stepIdx, paramIdx, stepIdx, paramIdx, ..., stepIdx
    // The last part is always a step index (odd total parts = step at the end)
    // Even total parts would mean the pathPrefix ends at a param (not a step), which is invalid here

    // If only 1 part: it's a main chain step index
    // If 3 parts: chainSteps[parts[0]].params[parts[1]].subSteps[parts[2]]
    // If 5 parts: chainSteps[parts[0]].params[parts[1]].subSteps[parts[2]].params[parts[3]].subSteps[parts[4]]
    for (var i = 0; i < parts.length - 1; i += 2) {
        var stepIdx = parseInt(parts[i]);
        var paramIdx = parseInt(parts[i + 1]);
        var step = steps[stepIdx];
        if (!step || !step.params || paramIdx >= step.params.length) return;
        var param = step.params[paramIdx];
        if (param.callType !== 'subchain' || !param.subSteps) return;
        steps = param.subSteps;
    }
    var removeIdx = parseInt(parts[parts.length - 1]);

    if (steps.length <= 1) {
        steps.splice(0, 1);
    } else if (removeIdx === 0) {
        // First step removed ? next step inherits className if needed
        var next = steps[1];
        if (!next.className && steps[0].className) {
            next.className = steps[0].className;
            next.isStatic = steps[0].isStatic;
        }
        steps.splice(0, 1);
    } else {
        steps.splice(removeIdx, 1);
    }
    // Keep sub-chain editor expanded if removing from a sub-chain
    if (parts.length > 1) {
        // Sub-chain removal ? keep the sub-chain param path expanded
        // pathPrefix format: "0:2:1" ? parent path is "0:2" (the subchain param)
        var paramPath = parts.slice(0, parts.length - 1).join(':');
        expandedEditors[paramPath] = true;
    }
    clearAllResults();
    renderChain();
}

function clearChain() {
    chainSteps = [];
    expandedEditors = {};
    $('#invoke_result').hide();
    renderChain();
}

// =============================================
// Sub-chain manipulation
// =============================================

function addSubStep(paramPath) {
    msContext = { action: 'addSubStep', pathStr: paramPath };
    showMemberSelector();
}

function addNextSubStep(pathPrefix) {
    // pathPrefix is the path to the sub-chain's parent param (e.g., "0:2")
    var subSteps = getSubStepsByPathPrefix(pathPrefix);
    if (!subSteps || subSteps.length === 0) {
        addSubStep(pathPrefix);
        return;
    }
    var lastSub = subSteps[subSteps.length - 1];
    var realType = lastSub.realReturnType || '';

    // If we have the actual runtime class name from a previous invocation,
    // directly load its members — no need to re-execute the chain.
    if (realType && canChainFrom(shortClassName(realType))) {
        msContext = { action: 'addNextSubStep', pathStr: pathPrefix, returnTypeHint: shortClassName(realType), currentClassName: realType, currentLoaderId: lastSub.loaderId || 0 };
        loadMembersForModal(realType, lastSub.loaderId || 0);
    } else {
        // No realReturnType available — peek first
        invokeForPeekSub(pathPrefix);
    }
}

// =============================================
// Param mode switching
// =============================================

function switchParamMode(paramPath, newMode) {
    var param = getParamByPath(paramPath);
    if (!param) return;
    var step = getStepByPath(paramPath);
    var paramIdx = parseInt(paramPath.split(':').pop());
    var typeHint = param._paramTypeHint || (step ? getParamTypeHint(step, paramIdx) : '') || '';

    if (newMode === 'null') {
        replaceParamByPath(paramPath, createNullParam(typeHint));
    } else if (newMode === 'literal') {
        var valueType = typeHint;
        if (!isPrimitiveType(valueType) && valueType !== 'String') valueType = 'String';
        replaceParamByPath(paramPath, createLiteralParam('', valueType));
    } else if (newMode === 'subchain') {
        replaceParamByPath(paramPath, createSubchainParam(typeHint));
    }

    if (newMode === 'subchain') {
        expandedEditors[paramPath] = true;  // auto-expand sub-chain
    } else {
        delete expandedEditors[paramPath];  // no editor needed for null/literal (inline)
    }
    clearAllResults();
    renderChain();
}

function updateParamValue(paramPath, value) {
    var param = getParamByPath(paramPath);
    if (param && param.callType === 'literal') {
        param.value = value;
        clearAllResults();
    }
}

// =============================================
// Auto-peek strategy
// =============================================

function invokeForPeek() {
    if (chainSteps.length === 0) { alert('Add at least one step first'); return; }

    msContext = { action: 'addNextStepAfterPeek' };

    var json = serializeToChain();
    $.ajax({
        url: '/invoke/execMembers.json',
        type: 'POST',
        contentType: 'application/json',
        data: json,
        success: function(responseStr) {
            var ret;
            try { ret = JSON.parse(responseStr); } catch(e) { alert('Invalid response'); return; }
            if (ret.status) {
                var data = ret.data;
                // Update invoke results into the chain
                var invokeResult = data.invokeResult;
                updateResultsFromChain(invokeResult, chainSteps.length);
                renderChain();

                var lastStep = chainSteps[chainSteps.length - 1];
                var runtimeClassName = data.runtimeClassName || '';
                var members = data.members || [];

                // If invocation failed, show error
                if (lastStep.exception) {
                    alert('Invoke failed: ' + lastStep.exception);
                    return;
                }

                // Determine actual return type from runtime class name
                var actualReturnType = runtimeClassName;
                if (!actualReturnType) {
                    // Fallback: use returnType from the invoke result
                    actualReturnType = lastStep.returnType || '';
                    if (actualReturnType === 'Object' && lastStep.returnValue) {
                        var extracted = extractClassNameFromReturnValue(lastStep.returnValue);
                        if (extracted) actualReturnType = extracted;
                    }
                }
                // Set short name for display
                var shortName = shortClassName(actualReturnType);
                lastStep._returnTypeHint = shortName;

                if (!actualReturnType || !canChainFrom(shortName)) {
                    // Return type is void/primitive — no further chaining possible
                    msContext = { action: 'addNextStep' };
                    showMemberSelector();
                    return;
                }

                // If we got members from runtime type, show them directly
                if (members.length > 0) {
                    msContext.currentClassName = actualReturnType;
                    msContext.currentLoaderId = lastStep.loaderId || 0;
                    msContext.action = 'addNextStep';
                    msContext.returnTypeHint = shortName;

                    $('#ms_modal_title').text('Select Member');
                    $('#ms_class_label').text(actualReturnType);
                    $('#ms_change_class_btn').show();
                    $('#ms_class_search_area').hide();
                    renderMemberList(members, actualReturnType);
                    $('#member_select_modal').modal('show');
                } else {
                    // No members available (e.g., primitive return type) — fall back to class search
                    msContext = { action: 'addNextStep', returnTypeHint: shortName };
                    autoSearchByReturnType(shortName);
                }
            } else {
                alert('Invoke peek failed: ' + ret.msg);
            }
        },
        error: function(xhr) { alert('HTTP error: ' + xhr.status); }
    });
}

function invokeForPeekSub(pathPrefix) {
    var subSteps = getSubStepsByPathPrefix(pathPrefix);
    if (!subSteps || subSteps.length === 0) {
        addSubStep(pathPrefix);
        return;
    }

    msContext = { action: 'addNextSubStepAfterPeek', pathStr: pathPrefix };

    var json = serializeToChain();
    $.ajax({
        url: '/invoke/execMembers.json',
        type: 'POST',
        contentType: 'application/json',
        data: json,
        success: function(responseStr) {
            var ret;
            try { ret = JSON.parse(responseStr); } catch(e) { alert('Invalid response'); return; }
            if (ret.status) {
                var data = ret.data;
                var invokeResult = data.invokeResult;
                updateResultsFromChain(invokeResult, chainSteps.length);
                renderChain();

                var lastSub = subSteps[subSteps.length - 1];
                var runtimeClassName = data.runtimeClassName || '';
                var members = data.members || [];

                if (lastSub.exception) {
                    alert('Sub-chain invoke failed: ' + lastSub.exception);
                    return;
                }

                var actualReturnType = runtimeClassName;
                if (!actualReturnType) {
                    actualReturnType = lastSub.returnType || '';
                    if (actualReturnType === 'Object' && lastSub.returnValue) {
                        var extracted = extractClassNameFromReturnValue(lastSub.returnValue);
                        if (extracted) actualReturnType = extracted;
                    }
                }
                var shortName = shortClassName(actualReturnType);
                lastSub._returnTypeHint = shortName;

                if (!actualReturnType || !canChainFrom(shortName)) {
                    msContext = { action: 'addNextSubStep', pathStr: pathPrefix };
                    showMemberSelector();
                    return;
                }

                if (members.length > 0) {
                    msContext.currentClassName = actualReturnType;
                    msContext.currentLoaderId = lastSub.loaderId || 0;
                    msContext.action = 'addNextSubStep';
                    msContext.pathStr = pathPrefix;
                    msContext.returnTypeHint = shortName;

                    $('#ms_modal_title').text('Select Member');
                    $('#ms_class_label').text(actualReturnType);
                    $('#ms_change_class_btn').show();
                    $('#ms_class_search_area').hide();
                    renderMemberList(members, actualReturnType);
                    $('#member_select_modal').modal('show');
                } else {
                    msContext = { action: 'addNextSubStep', pathStr: pathPrefix, returnTypeHint: shortName };
                    autoSearchByReturnType(shortName);
                }
            } else {
                alert('Invoke peek failed: ' + ret.msg);
            }
        },
        error: function(xhr) { alert('HTTP error: ' + xhr.status); }
    });
}

function autoSearchByReturnType(returnType) {
    // If the return type is an array, strip "[]" and search for the element type
    var searchType = returnType;
    if (searchType.endsWith('[]')) {
        searchType = searchType.substring(0, searchType.length - 2);
    }

    $.post('/invoke/classes.json', {prefix: searchType}, function(str) {
        var ret;
        try { ret = JSON.parse(str); } catch(e) {
            showMemberSelector();
            return;
        }
        if (ret.status && ret.data && ret.data.length > 0) {
            var bestMatch = null;
            for (var i = 0; i < ret.data.length; i++) {
                var cn = ret.data[i].name;
                var simple = shortClassName(cn);
                if (simple === returnType) { bestMatch = ret.data[i]; break; }
            }
            if (!bestMatch) bestMatch = ret.data[0];

            msContext.currentClassName = bestMatch.name;
            msContext.currentLoaderId = bestMatch.loaderId;
            loadMembersForModal(bestMatch.name, bestMatch.loaderId);
        } else {
            showMemberSelector();
        }
    });
}

// =============================================
// Member Selector modal
// =============================================

var msContext = {
    action: '',
    pathStr: '',
    returnTypeHint: '',
    currentClassName: '',
    currentLoaderId: 0,
    members: []
};

function showMemberSelector() {
    msContext.currentClassName = '';
    msContext.currentLoaderId = 0;
    $('#ms_modal_title').text('Select Member');
    $('#ms_class_label').text('Search a class first');
    $('#ms_change_class_btn').hide();
    $('#ms_class_search_area').show();
    $('#ms_class_search_input').val('');
    $('#ms_class_list').html('');
    $('#ms_member_list').html('<div style="color:#999;padding:8px;">Search for a class, then select a method or field.</div>');
    $('#member_select_modal').modal('show');
}


function msShowClassSearch() {
    $('#ms_class_search_area').toggle();
    if ($('#ms_class_search_area').is(':visible')) {
        $('#ms_class_search_input').val('').focus();
        $('#ms_class_list').html('');
    }
}

function msSearchClasses() {
    var prefix = $('#ms_class_search_input').val().trim();
    if (!prefix) { alert('Type a class name'); return; }

    $.post('/invoke/classes.json', {prefix: prefix}, function(str) {
        var ret;
        try { ret = JSON.parse(str); } catch(e) { return; }
        if (ret.status && ret.data && ret.data.length > 0) {
            var html = '';
            for (var i = 0; i < ret.data.length; i++) {
                var cn = ret.data[i].name;
                var lid = ret.data[i].loaderId;
                html += '<div class="class-item" onclick="msSelectClass(\'' + escapeAttr(cn) + '\',' + lid + ')">';
                html += '<span style="font-weight:bold;font-size:13px;">' + escapeHtml(cn) + '</span>';
                if (lid > 0) html += ' <span style="color:#d9534f;font-size:11px;">[loader#' + lid + ']</span>';
                html += '</div>';
            }
            $('#ms_class_list').html(html);
        } else {
            $('#ms_class_list').html('<div style="color:#999;">No classes found for "' + escapeHtml(prefix) + '"</div>');
        }
    });
}

function msSelectClass(className, loaderId) {
    msContext.currentClassName = className;
    msContext.currentLoaderId = loaderId;
    loadMembersForModal(className, loaderId);
}

function loadMembersForModal(className, loaderId) {
    $.post('/invoke/members.json', {className: className}, function(str) {
        var ret;
        try { ret = JSON.parse(str); } catch(e) {
            showMemberSelector();
            return;
        }
        if (ret.status && ret.data) {
            msContext.members = ret.data;
            $('#ms_class_label').text(className);
            $('#ms_change_class_btn').show();
            $('#ms_class_search_area').hide();
            renderMemberList(ret.data, className);
            if (!$('#member_select_modal').hasClass('in')) {
                $('#member_select_modal').modal('show');
            }
        } else {
            showMemberSelector();
        }
    });
}

function renderMemberList(members, className) {
    var searchedSimpleName = shortClassName(className);
    var isFirstStep = msContext.action === 'addFirstStep';
    var methods = [];
    var fields = [];
    for (var i = 0; i < members.length; i++) {
        // First step: only show static members (no receiver available)
        if (isFirstStep && !members[i].isStatic) continue;
        if (members[i].isField) fields.push(members[i]);
        else methods.push(members[i]);
    }

    var methodGroups = {};
    var methodOrder = [];
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var key = m.name + (m.isStatic ? '_static' : '_instance');
        if (!methodGroups[key]) { methodGroups[key] = []; methodOrder.push(key); }
        methodGroups[key].push(m);
    }

    var html = '';
    if (methodOrder.length > 0) {
        html += '<div class="member-section-header">Methods (' + methods.length + ')</div>';
        for (var g = 0; g < methodOrder.length; g++) {
            var group = methodGroups[methodOrder[g]];
            var baseMethod = group[0];
            if (group.length === 1) {
                html += renderSingleMethod(baseMethod, className, false, searchedSimpleName);
            } else {
                html += '<div style="margin:2px 0;">';
                html += '<div style="font-weight:bold;padding:3px 6px;color:#31708f;font-size:13px;">';
                html += escapeHtml(baseMethod.name);
                if (baseMethod.isStatic) html += ' <span class="type-badge badge-static">static</span>';
                html += ' <span style="color:#999;font-size:11px;">? ' + group.length + ' overloads</span>';
                html += '</div>';
                var sorted = group.slice().sort(function(a, b) {
                    return (a.paramTypes ? a.paramTypes.length : 0) - (b.paramTypes ? b.paramTypes.length : 0);
                });
                for (var s = 0; s < sorted.length; s++) {
                    html += renderSingleMethod(sorted[s], className, true, searchedSimpleName);
                }
                html += '</div>';
            }
        }
    }

    if (fields.length > 0) {
        html += '<div class="member-section-header">Fields (' + fields.length + ')</div>';
        for (var i = 0; i < fields.length; i++) {
            var f = fields[i];
            var info = JSON.stringify({
                className: className, name: f.name, returnType: f.returnType,
                isStatic: f.isStatic, isField: true,
                declaringClass: f.declaringClass || '',
                loaderId: msContext.currentLoaderId || 0
            });
            html += '<div class="member-item field-item" onclick="msSelectMember(this)" data-member-info="' + escapeAttr(info) + '">';
            html += '<div><span class="member-name">' + escapeHtml(f.name) + '</span>';
            if (f.isStatic) html += ' <span class="type-badge badge-static">static</span>';
            if (f.declaringClass && f.declaringClass !== searchedSimpleName) {
                html += ' <span class="badge-inherited">from ' + escapeHtml(f.declaringClass) + '</span>';
            }
            html += '</div>';
            html += '<div class="member-sig">' + escapeHtml(f.returnType) + '</div>';
            html += '</div>';
        }
    }

    html += '<div class="member-section-header">Other</div>';
    html += '<div class="member-item" onclick="msSelectClassRef()" style="background:#d9edf7;">';
    html += '<div><span class="member-name" style="color:#31708f;">Class.forName</span></div>';
    html += '<div class="member-sig">Reference class as Class&lt;?&gt;</div>';
    html += '</div>';

    $('#ms_member_list').html(html);
}

function renderSingleMethod(m, className, isOverload, searchedSimpleName) {
    var info = JSON.stringify({
        className: className, name: m.name, returnType: m.returnType,
        paramTypes: m.paramTypes || [], isStatic: m.isStatic, isField: false,
        declaringClass: m.declaringClass || '',
        loaderId: msContext.currentLoaderId || 0
    });
    var indent = isOverload ? ' style="padding-left:16px;"' : '';
    var html = '<div class="member-item" onclick="msSelectMember(this)" data-member-info="' + escapeAttr(info) + '"' + indent + '>';
    html += '<div><span class="member-name">' + escapeHtml(m.name);
    if (m.paramTypes && m.paramTypes.length > 0) {
        html += '(' + m.paramTypes.join(', ') + ')';
    } else {
        html += '()';
    }
    html += '</span>';
    if (m.declaringClass && m.declaringClass !== searchedSimpleName) {
        html += ' <span class="badge-inherited">from ' + escapeHtml(m.declaringClass) + '</span>';
    }
    if (!isOverload && m.isStatic) html += ' <span class="type-badge badge-static">static</span>';
    html += '</div>';
    html += '<div class="member-sig">' + escapeHtml(m.returnType) + '</div>';
    html += '</div>';
    return html;
}

function msSelectMember(elem) {
    var infoStr = elem.getAttribute('data-member-info');
    var info = JSON.parse(infoStr);

    var newNode;
    if (info.isField) {
        newNode = createFieldStep(info.className, info.name, info.isStatic, info.loaderId);
    } else {
        newNode = createMethodStep(info.className, info.name,
            (info.paramTypes && info.paramTypes.length > 0) ? info.paramTypes.join(',') : '',
            info.isStatic, info.loaderId);
        newNode._returnTypeHint = info.returnType;
        newNode.params = [];
        if (info.paramTypes && info.paramTypes.length > 0) {
            for (var i = 0; i < info.paramTypes.length; i++) {
                var t = info.paramTypes[i];
                var paramNode = createAutoParam(t);
                paramNode._paramTypeHint = t;
                newNode.params.push(paramNode);
            }
        }
    }
    newNode._returnTypeHint = info.returnType;

    applyMemberSelection(newNode);
    $('#member_select_modal').modal('hide');
}

function msSelectClassRef() {
    var className = msContext.currentClassName;
    var loaderId = msContext.currentLoaderId;
    var newNode = createClassRefStep(className, loaderId);
    applyMemberSelection(newNode);
    $('#member_select_modal').modal('hide');
}

function applyMemberSelection(newNode) {
    var action = msContext.action;

    if (action === 'addFirstStep') {
        chainSteps.push(newNode);
    } else if (action === 'addNextStep' || action === 'addNextStepAfterPeek') {
        if (chainSteps.length > 0) {
            newNode.className = '';  // resolved from target runtime type
            newNode.isStatic = false;
        }
        chainSteps.push(newNode);
    } else if (action === 'addSubStep' || action === 'addNextSubStep' || action === 'addNextSubStepAfterPeek') {
        var subSteps = getSubStepsByPathPrefix(msContext.pathStr);
        if (subSteps) {
            if (subSteps.length > 0) {
                newNode.className = '';
                newNode.isStatic = false;
            }
            subSteps.push(newNode);
        }
    }

    // Keep relevant editors expanded ? sub-chain operations should not collapse the sub-chain
    if (action === 'addSubStep' || action === 'addNextSubStep' || action === 'addNextSubStepAfterPeek') {
        expandedEditors[msContext.pathStr] = true;
    }
    // For main chain actions, keep existing sub-chain editors expanded
    // Don't clear expandedEditors unless it's a structural change

    clearAllResults();
    renderChain();
}

// =============================================
// Invoke
// =============================================

/** Invoke the full chain (all steps). */
function invokeChain() {
    if (chainSteps.length === 0) { alert('Add at least one step first'); return; }
    invokeChainUpTo(chainSteps.length - 1);
}

/** Invoke the chain up to step N (inclusive). Only serializes steps 0..N. */
function invokeChainUpTo(stepIndex) {
    if (chainSteps.length === 0 || stepIndex < 0) { alert('Add at least one step first'); return; }
    var upTo = Math.min(stepIndex, chainSteps.length - 1);

    // Clear all previous results before invoking to avoid stale data
    clearAllResults();

    var json = serializeChainRange(chainSteps, 0, upTo + 1);
    $.ajax({
        url: '/invoke/exec',
        type: 'POST',
        contentType: 'application/json',
        data: json,
        success: function(responseStr) {
            var ret;
            try { ret = JSON.parse(responseStr); } catch(e) { alert('Invalid response'); return; }
            if (ret.status) {
                updateResultsFromChain(ret.data, upTo + 1);
                renderChain();
                showChainResult();
            } else {
                $('#invoke_result').show();
                $('#result_container').html('<div style="color:#a94442;padding:8px;">' + escapeHtml(ret.msg) + '</div>');
            }
        },
        error: function(xhr) { alert('HTTP error: ' + xhr.status); }
    });
}

/** Invoke a sub-chain independently. pathPrefix points to the sub-chain param. */
function invokeSubChain(pathPrefix) {
    var subSteps = getSubStepsByPathPrefix(pathPrefix);
    if (!subSteps || subSteps.length === 0) { alert('Add at least one sub-chain step'); return; }

    var json = serializeChainRange(subSteps, 0, subSteps.length);
    $.ajax({
        url: '/invoke/exec',
        type: 'POST',
        contentType: 'application/json',
        data: json,
        success: function(responseStr) {
            var ret;
            try { ret = JSON.parse(responseStr); } catch(e) { alert('Invalid response'); return; }
            if (ret.status) {
                // Merge results back into the sub-steps (response is flat chain array)
                var respChain = ret.data;
                for (var j = 0; j < subSteps.length && j < respChain.length; j++) {
                    mergeResponseToStep(subSteps[j], respChain[j]);
                }
                // Update _returnTypeHint on the last sub-step so the parent step knows the type
                if (subSteps.length > 0 && respChain.length > 0) {
                    var lastSub = subSteps[subSteps.length - 1];
                    var lastResp = respChain[respChain.length - 1];
                    if (lastResp.returnType) {
                        lastSub._returnTypeHint = lastResp.returnType;
                        // Also update the parent step's param _paramTypeHint if possible
                        var param = getParamByPath(pathPrefix);
                        if (param && lastResp.returnType) {
                            // The sub-chain's return type replaces the param's _paramTypeHint
                        }
                    }
                }
                renderChain();
            } else {
                alert('Sub-chain invoke failed: ' + escapeHtml(ret.msg));
            }
        },
        error: function(xhr) { alert('HTTP error: ' + xhr.status); }
    });
}

function showChainResult() {
    $('#invoke_result').show();
    if (chainSteps.length === 0) {
        $('#result_container').html('<div class="text-muted">No result data.</div>');
        return;
    }

    var html = '<div>';
    for (var i = 0; i < chainSteps.length; i++) {
        var step = chainSteps[i];
        var hasError = step.exception && step.exception.length > 0;
        var isCascade = isCascadeException(step.exception);

        // Three visual states: success (green), cascade (yellow/gray), root-cause failure (red)
        var cls;
        if (!hasError) {
            cls = 'result-step';
        } else if (isCascade) {
            cls = 'result-step result-cascade';
        } else {
            cls = 'result-step result-error';
        }

        html += '<div class="' + cls + '">';
        if (i > 0) html += '<span style="color:#5bc0de;">? </span>';
        if (step.className) html += escapeHtml(shortClassName(step.className)) + '.';
        html += '<span class="result-sig">' + escapeHtml(step.methodName || shortClassName(step.className)) + '</span>';

        if (hasError) {
            if (isCascade) {
                html += ' <span class="result-value-cascade">CASCADE: ' + escapeHtml(truncate(extractRootCause(step.exception), maxDisplayLen)) + '</span>';
                html += '<span class="result-meta">(blocked by upstream failure)</span>';
            } else {
                html += ' <span class="result-value-error">FAILED: ' + escapeHtml(truncate(step.exception, maxDisplayLen)) + '</span>';
            }
        } else {
            html += ' <span class="result-value">=> ' + escapeHtml(step.returnValue || 'void') + '</span>';
            html += '<span class="result-meta">(' + escapeHtml(step.returnType || '?') + ', ' + step.duration + 'ms)</span>';
            if (step.arrayLength >= 0) html += '<span class="result-meta"> [length=' + step.arrayLength + ']</span>';
        }
        html += '</div>';
    }
    html += '</div>';
    $('#result_container').html(html);
}

// =============================================
// Serialization: flat array ? target-linked tree
// =============================================

/**
 * Convert flat chainSteps[] to MethodCallNode target-linked tree for POST.
 * chainSteps[0] = innermost (no target), chainSteps[last] = root.
 * Build outward: each new step wraps the previous as its target.
 */
function serializeToChain() {
    if (chainSteps.length === 0) return null;
    var chain = [];
    for (var i = 0; i < chainSteps.length; i++) {
        var node = cloneStepForSerialization(chainSteps[i]);
        // In the flat chain format, the relationship between steps is implicit by position:
        // chain[i+1] uses chain[i]'s result as receiver. No "target" field needed.
        chain.push(node);
    }
    return JSON.stringify({ chain: chain }, null, 2);
}

/** Serialize a range of steps [start, end) into a flat chain array format. */
function serializeChainRange(steps, start, end) {
    if (!steps || steps.length === 0 || start >= end) return null;
    var chain = [];
    for (var i = start; i < end; i++) {
        var node = cloneStepForSerialization(steps[i]);
        chain.push(node);
    }
    return JSON.stringify({ chain: chain }, null, 2);
}

function cloneStepForSerialization(step) {
    var node = {};
    node.callType = step.callType || 'method';
    node.className = step.className || '';
    if (step.methodName) node.methodName = step.methodName;
    if (step.index >= 0) node.index = step.index;
    if (step.callType === 'capturedRef') {
        node.capturedValue = step.capturedValue || '';
        node.capturedParamIndex = step.capturedParamIndex >= 0 ? step.capturedParamIndex : 0;
    }
    var pt = normalizeParamTypes(step.paramTypes);
    if (pt && pt.trim()) {
        node.paramTypes = pt.split(',').map(function(s) { return s.trim(); });
    }
    if (step.loaderId > 0) node.loaderId = step.loaderId;

    // Serialize params recursively (handles sub-chains, literals, classRefs, null)
    if (step.params && step.params.length > 0) {
        node.params = step.params.map(function(p) { return serializeParam(p); });
    }
    return node;
}

function serializeParam(param) {
    if (param.callType === 'literal') {
        return { value: param.value, valueType: param.valueType, callType: 'literal' };
    }
    if (param.callType === 'null') {
        return { value: 'null', valueType: 'null', callType: 'literal' };
    }
    if (param.callType === 'classRef') {
        return { className: param.className || '', callType: 'classRef', loaderId: param.loaderId || 0 };
    }
    if (param.callType === 'capturedRef') {
        return { className: param.className || '', callType: 'capturedRef', capturedValue: param.capturedValue || '', capturedParamIndex: param.capturedParamIndex >= 0 ? param.capturedParamIndex : 0 };
    }
    if (param.callType === 'subchain') {
        var subSteps = param.subSteps || [];
        if (subSteps.length === 0) {
            return { value: 'null', valueType: 'null', callType: 'literal' };
        }
        // Convert subSteps[] to target-linked tree (same logic as main chain)
        var tree = null;
        for (var i = 0; i < subSteps.length; i++) {
            var node = cloneStepForSerialization(subSteps[i]);
            if (tree) { node.target = tree; node.isStatic = false; }
            tree = node;
        }
        return tree;
    }
    // Fallback
    return { value: 'null', valueType: 'null', callType: 'literal' };
}

/**
 * Deserialize a backend response into chainSteps[].
 * Supports two formats:
 * 1. Flat chain array (current): ret.data = [node0, node1, node2, ...]
 * 2. Target-linked tree (legacy): ret.data = { className, methodName, target: { ... } }
 */
function deserializeFromResponse(data) {
    if (!data) return;
    // Check if data is an array (flat chain format)
    if (Array.isArray(data)) {
        chainSteps = [];
        for (var i = 0; i < data.length; i++) {
            var step = data[i];
            // Normalize paramTypes
            if (step.paramTypes) step.paramTypes = normalizeParamTypes(step.paramTypes);
            convertParamsFromTree(step);
            chainSteps.push(step);
        }
    } else {
        // Legacy target-linked tree format
        chainSteps = flattenTreeToSteps(data);
        for (var i = 0; i < chainSteps.length; i++) {
            convertParamsFromTree(chainSteps[i]);
        }
    }
}

function flattenTreeToSteps(tree) {
    if (!tree) return [];
    var steps = [];
    if (tree.target) steps = flattenTreeToSteps(tree.target);
    // Skip pure-literal nodes
    if (tree.callType !== 'literal' && !(tree.value !== undefined && tree.valueType !== undefined && !tree.className && !tree.methodName)) {
        // Normalize paramTypes from backend response format (array) to our model format (string)
        if (tree.paramTypes) tree.paramTypes = normalizeParamTypes(tree.paramTypes);
        steps.push(tree);
    }
    return steps;
}

function convertParamsFromTree(step) {
    if (!step || !step.params || step.params.length === 0) return;
    for (var i = 0; i < step.params.length; i++) {
        var param = step.params[i];
        if (param.callType === 'literal') {
            param._paramTypeHint = param.valueType || '';
        } else if (param.callType === 'classRef') {
            param._paramTypeHint = 'Class';
        } else if (param.callType === 'capturedRef') {
            param._paramTypeHint = param.className || '';
        } else if (param.callType === 'null') {
            param._paramTypeHint = '';
        } else if (param.callType === 'method' || param.callType === 'field' || (param.className && !param.callType)) {
            // This param is a call chain ? convert to subchain
            var subSteps = flattenTreeToSteps(param);
            for (var j = 0; j < subSteps.length; j++) {
                convertParamsFromTree(subSteps[j]);
            }
            var typeHint = step.paramTypes ? (step.paramTypes[i] || '') : '';
            step.params[i] = {
                callType: 'subchain',
                subSteps: subSteps,
                _paramTypeHint: typeHint
            };
        } else if (param.value === 'null' && param.valueType === 'null') {
            param.callType = 'null';
            param._paramTypeHint = '';
        }
    }
}

/**
 * Update chainSteps with response data from backend.
 * The response is now a flat chain array: [step0, step1, step2, ...]
 * where data[i] corresponds directly to chainSteps[i].
 * Much simpler than the old target-linked tree traversal.
 *
 * count: how many steps from chainSteps to update (from chainSteps[0] to chainSteps[count-1]).
 * If not specified, defaults to chainSteps.length.
 */
function updateResultsFromChain(chainData, count) {
    if (!chainData || chainSteps.length === 0) return;
    var n = (count !== undefined && count !== null) ? Math.min(count, chainSteps.length) : chainSteps.length;
    if (n <= 0) return;

    for (var i = 0; i < n && i < chainData.length; i++) {
        console.log('[merge] chainSteps[' + i + '] "' + (chainSteps[i].methodName || chainSteps[i].className) + '" ↔ resp "' + (chainData[i].methodName || chainData[i].className || '?') + '" exception=' + (chainData[i].exception || 'null') + ' returnType=' + (chainData[i].returnType || 'null'));
        mergeResponseToStep(chainSteps[i], chainData[i]);
    }
}

function mergeResponseToStep(model, resp) {
    if (!model || !resp) return;
    // Always clear exception first — if the backend response doesn't include it,
    // the step succeeded and any previous exception must be cleared.
    // Backend omits "exception" field when null (success), so resp.exception is undefined.
    model.exception = resp.exception || null;
    // returnType and returnValue: use resp value if present, clear to empty if not.
    // Backend omits these when null (failed step), so resp fields are undefined.
    model.returnType = resp.returnType || '';
    model.realReturnType = resp.realReturnType || '';
    model.returnValue = resp.returnValue || '';
    model.isVoid = resp.isVoid !== undefined ? resp.isVoid : false;
    model.duration = resp.duration !== undefined ? resp.duration : 0;
    if (resp.arrayLength !== undefined && resp.arrayLength >= 0) model.arrayLength = resp.arrayLength;
    if (resp.capturedValue) model.capturedValue = resp.capturedValue;
    if (resp.capturedParamIndex !== undefined && resp.capturedParamIndex >= 0) model.capturedParamIndex = resp.capturedParamIndex;
    if (!model._returnTypeHint && resp.returnType) model._returnTypeHint = resp.returnType;
    // realReturnType gives the full runtime class name — use it for next-step resolution
    if (resp.realReturnType) model.realReturnType = resp.realReturnType;
    // Normalize paramTypes from response (backend returns array, our model uses string)
    if (resp.paramTypes) model.paramTypes = normalizeParamTypes(resp.paramTypes);

    // Merge params recursively
    if (model.params && resp.params) {
        for (var i = 0; i < model.params.length && i < resp.params.length; i++) {
            var mParam = model.params[i];
            var rParam = resp.params[i];

            if (mParam.callType === 'literal' || mParam.callType === 'null') {
                if (rParam.returnType) mParam.returnType = rParam.returnType;
                if (rParam.returnValue) mParam.returnValue = rParam.returnValue;
            } else if (mParam.callType === 'classRef') {
                if (rParam.returnType) mParam.returnType = rParam.returnType;
                if (rParam.returnValue) mParam.returnValue = rParam.returnValue;
            } else if (mParam.callType === 'subchain') {
                // The response param is a target-linked tree (method chain).
                // Instead of flattenTreeToSteps which can misalign due to skip logic,
                // walk the target chain from outermost→innermost and match with
                // subSteps in reverse order (subSteps[last]=outermost, subSteps[0]=innermost).
                var mSubSteps = mParam.subSteps || [];
                if (mSubSteps.length > 0) {
                    // Walk the response tree to find the node matching each sub-step
                    // The response param IS the outermost sub-step.
                    // Its target chain goes: outermost → ... → innermost.
                    // Our subSteps order is: [innermost, ..., outermost].
                    // So we match subSteps[last] with rParam, subSteps[last-1] with rParam.target, etc.
                    var respNode = rParam;
                    for (var k = mSubSteps.length - 1; k >= 0; k--) {
                        if (respNode) {
                            mergeResponseToStep(mSubSteps[k], respNode);
                            respNode = respNode.target || null;
                        }
                    }
                }
            } else if (mParam.callType === 'method' || mParam.callType === 'field') {
                // The response param is a chain node ? merge directly
                // This happens when the model wasn't converted to subchain yet
                if (rParam.returnType) mParam.returnType = rParam.returnType;
                if (rParam.returnValue) mParam.returnValue = rParam.returnValue;
            }
        }
    }
}

// =============================================
// JSON Editor
// =============================================

function openJsonEditor() {
    var json = serializeToChain();
    $('#json_textarea').val(json || '');
    $('#json_modal').modal('show');
}

function exportFormToJson() {
    var json = serializeToChain();
    $('#json_textarea').val(json || '');
}

function importJsonToForm() {
    var text = $('#json_textarea').val().trim();
    if (!text) { alert('Paste JSON first'); return; }
    try {
        var json = JSON.parse(text);
        // Support both chain array format and legacy target-linked tree
        if (json.chain) {
            // Flat chain format: { chain: [step0, step1, ...] }
            chainSteps = [];
            for (var i = 0; i < json.chain.length; i++) {
                var step = json.chain[i];
                // Normalize paramTypes
                if (step.paramTypes) step.paramTypes = normalizeParamTypes(step.paramTypes);
                convertParamsFromTree(step);
                chainSteps.push(step);
            }
        } else {
            // Legacy target-linked tree format
            deserializeFromResponse(json);
        }
        expandedEditors = {};
        renderChain();
    } catch(e) { alert('JSON parse error: ' + e.message); }
}

// =============================================
// Init
// =============================================

$(document).ready(function() {
    renderChain();

    $('#ms_class_search_input').on('keypress', function(e) {
        if (e.which === 13) { e.preventDefault(); msSearchClasses(); }
    });

    $('#weave_class_search_input').on('keypress', function(e) {
        if (e.which === 13) { e.preventDefault(); weaveSearchClasses(); }
    });

    // Restore weave state from backend on page load
    restoreWeaveState();
});

// =============================================
// Weave (Param Capture)
// =============================================

var weaveClassName = '';
var weaveMethodDesc = '';
var weaveSelectedMethodRaw = '';  // original (unsanitized) method name for display
var weaveSelectedParamTypeFqns = [];  // fully qualified param type names for Method resolution

function restoreWeaveState() {
    $.get('/invoke/weave-status.json', function(str) {
        var ret;
        try { ret = JSON.parse(str); } catch(e) { return; }
        if (ret.status && ret.data) {
            var data = ret.data;
            if (data.className) {
                weaveClassName = data.className;
                weaveMethodDesc = data.methodName || '';
                weaveSelectedMethodRaw = data.methodName || '';
                weaveSelectedParamTypeFqns = data.paramTypes || [];
                $('#weave_status').html('Weaved: <b style="color:#3c763d;">' + weaveClassName + '#' + weaveMethodDesc + '</b>');
                $('#unweave_btn').show();
                // Also restore captured params if available
                if (data.capturedParams && data.capturedParams.length > 0) {
                    renderCapturedParams(data.capturedParams);
                }
                // Auto-populate chain from weave if chain is empty
                if (chainSteps.length === 0 && data.capturedParams && data.capturedParams.length > 0) {
                    autoPopulateChainFromWeave();
                }
            } else {
                $('#weave_status').html('No weave target set');
                $('#unweave_btn').hide();
            }
        }
    });
}

function autoPopulateChainFromWeave() {
    $.get('/invoke/weave-chain.json', function(str) {
        var ret;
        try { ret = JSON.parse(str); } catch(e) { return; }
        if (ret.status && ret.data) {
            // Deserialize the tree into chainSteps[]
            deserializeFromResponse(ret.data);
            renderChain();
        }
    });
}

function renderCapturedParams(params) {
    if (!params || params.length === 0) {
        $('#weave_captured_params').html('No captured params yet — call the weaved method first');
        $('#use_params_btn').hide();
        return;
    }
    var html = '<b>Captured params:</b> ';
    for (var i = 0; i < params.length; i++) {
        var p = params[i];
        if (i > 0) html += ', ';
        if (p.isReceiver) {
            html += '<span style="color:#f0ad4e;font-weight:bold;">this</span> ';
            html += '<span style="color:#999;font-size:11px;">(' + p.type + ')</span> ';
            html += '<span style="color:#8a6d3b;">' + p.value + '</span>';
        } else {
            html += '<span style="color:#31708f;">param' + (i - 1) + '</span> ';
            html += '<span style="color:#999;font-size:11px;">(' + p.type + ')</span> ';
            html += '<span style="color:#3c763d;">' + p.value + '</span>';
        }
    }
    $('#weave_captured_params').html(html);
    $('#use_params_btn').show();
}

function showWeaveModal() {
    weaveClassName = '';
    weaveMethodDesc = '';
    weaveSelectedMethodRaw = '';
    $('#weave_class_label').text('Search a class first');
    $('#weave_change_class_btn').hide();
    $('#weave_class_search_area').show();
    $('#weave_class_search_input').val('');
    $('#weave_class_list').html('');
    $('#weave_method_list').hide().html('');
    $('#weave_btn').hide();
    $('#weave_modal').modal('show');
    $('#weave_class_search_input').focus();
}

function weaveShowClassSearch() {
    $('#weave_class_search_area').toggle();
    if ($('#weave_class_search_area').is(':visible')) {
        $('#weave_class_search_input').val('').focus();
        $('#weave_class_list').html('');
    }
}

function weaveSearchClasses() {
    var prefix = $('#weave_class_search_input').val().trim();
    if (!prefix) { alert('Type a class name prefix'); return; }
    $.post('/invoke/classes.json', {prefix: prefix}, function(str) {
        var ret = JSON.parse(str);
        if (ret.status && ret.data && ret.data.length > 0) {
            var html = '';
            for (var i = 0; i < ret.data.length; i++) {
                var cn = ret.data[i].name;
                var lid = ret.data[i].loaderId || 0;
                html += '<div class="class-item" onclick="weaveSelectClass(\'' + cn.replace(/'/g, "\\'") + '\',' + lid + ')">';
                html += '<span style="font-weight:bold;">' + cn + '</span>';
                if (lid > 0) html += ' <span style="color:#d9534f;font-size:11px;">[loader#' + lid + ']</span>';
                html += '</div>';
            }
            $('#weave_class_list').html(html);
        } else {
            $('#weave_class_list').html('<div style="color:#999;padding:6px;">No classes found</div>');
        }
    });
}

function weaveSelectClass(className, loaderId) {
    weaveClassName = className;
    weaveMethodDesc = '';
    weaveSelectedMethodRaw = '';
    $('#weave_class_label').text(className);
    $('#weave_change_class_btn').show();
    $('#weave_class_search_area').hide();
    $('#weave_method_list').show().html('<div style="color:#999;padding:8px;">Loading methods...</div>');
    $('#weave_btn').hide();

    $.post('/invoke/members.json', {className: className}, function(str) {
        var ret = JSON.parse(str);
        if (ret.status && ret.data) {
            var members = ret.data;
            var methodsWithParams = [];
            for (var i = 0; i < members.length; i++) {
                if (!members[i].isField && members[i].paramTypes && members[i].paramTypes.length > 0) {
                    methodsWithParams.push(members[i]);
                }
            }

            var html = '';
            if (methodsWithParams.length > 0) {
                html += '<div class="member-section-header">Methods with params (' + methodsWithParams.length + ')</div>';
                for (var i = 0; i < methodsWithParams.length; i++) {
                    var m = methodsWithParams[i];
                    var sanitized = m.name.replace(/</g, '_').replace(/>/g, '');
                    var info = JSON.stringify({name: m.name, paramTypes: m.paramTypes, paramTypeFqns: m.paramTypeFqns || [], returnType: m.returnType, isStatic: m.isStatic});
                    html += '<div class="member-item weave-method-item" onclick="weaveSelectMethod(this)" data-method-info="' + escapeAttr(info) + '" data-sanitized="' + escapeAttr(sanitized) + '">';
                    html += '<div><span class="member-name">' + m.name + '(' + m.paramTypes.join(', ') + ') → ' + m.returnType + '</span>';
                    if (m.isStatic) html += ' <span class="type-badge badge-static">static</span>';
                    html += '</div>';
                    html += '</div>';
                }
            } else {
                html = '<div style="color:#999;padding:6px;">No methods with parameters found</div>';
            }
            $('#weave_method_list').html(html);
        }
    });
}

function weaveSelectMethod(elem) {
    var infoStr = elem.getAttribute('data-method-info');
    var info = JSON.parse(infoStr);
    weaveMethodDesc = elem.getAttribute('data-sanitized');
    weaveSelectedMethodRaw = info.name;  // original method name (e.g., "<init>" or "foo")
    weaveSelectedParamTypeFqns = info.paramTypeFqns || [];

    // Highlight selected method
    $('.weave-method-item').css('background', '');
    $(elem).css('background', '#dff0d8');
    $('#weave_btn').show().html('<span class="glyphicon glyphicon-wrench"></span> Weave ' + weaveClassName + '#' + weaveSelectedMethodRaw);
}

function weaveMethod() {
    if (!weaveClassName || !weaveSelectedMethodRaw) { alert('Select a class and method first'); return; }
    var internalName = weaveClassName.replace(/\./g, '/');
    var methodName = weaveSelectedMethodRaw;  // ORIGINAL name, not sanitized (e.g., "<init>" or "foo")
    var paramTypesStr = weaveSelectedParamTypeFqns.join(',');  // comma-separated FQN string

    $.post('/invoke/weave.json', {className: internalName, methodName: methodName, paramTypes: paramTypesStr}, function(str) {
        var ret = JSON.parse(str);
        if (ret.status) {
            $('#weave_modal').modal('hide');
            $('#weave_status').html('Weaved: <b style="color:#3c763d;">' + weaveClassName + '#' + methodName + '</b>');
            $('#unweave_btn').show();
        } else {
            alert('Weave failed: ' + ret.msg);
        }
    });
}

function unweaveMethod() {
    if (!weaveClassName) { alert('No weave target'); return; }
    var internalName = weaveClassName.replace(/\./g, '/');

    $.post('/invoke/unweave.json', {className: internalName}, function(str) {
        var ret = JSON.parse(str);
        if (ret.status) {
            $('#weave_status').html('No weave target set');
            $('#unweave_btn').hide();
            $('#weave_captured_params').html('');
            $('#use_params_btn').hide();
        } else {
            alert('Unweave failed: ' + ret.msg);
        }
    });
}

function refreshCapturedParams() {
    $.get('/invoke/captured-params.json', function(str) {
        var ret = JSON.parse(str);
        if (ret.status) {
            renderCapturedParams(ret.data);
        } else {
            renderCapturedParams(null);
        }
    });
}

function useCapturedParamsAsInvoke() {
    // Directly invoke the weaved method using captured params via invokeCapture,
    // then populate chain editor with the result so user can continue chaining.
    $.get('/invoke/exec-capture.json', function(str) {
        var ret;
        try { ret = JSON.parse(str); } catch(e) { alert('Invalid response'); return; }
        if (ret.status && ret.data) {
            chainSteps = [];  // Clear existing chain
            deserializeFromResponse(ret.data);
            renderChain();
            showChainResult();
        } else {
            $('#invoke_result').show();
            $('#result_container').html('<div style="color:#a94442;padding:8px;">' + escapeHtml(ret.msg || 'unknown error') + '</div>');
        }
    }).fail(function(xhr) {
        alert('HTTP error: ' + xhr.status);
    });
}

// =============================================
// Init
// =============================================

$(document).ready(function() {
    renderChain();

    $('#ms_class_search_input').on('keypress', function(e) {
        if (e.which === 13) { e.preventDefault(); msSearchClasses(); }
    });

    $('#weave_class_search_input').on('keypress', function(e) {
        if (e.which === 13) { e.preventDefault(); weaveSearchClasses(); }
    });

    // Restore weave state from backend on page load
    restoreWeaveState();
});