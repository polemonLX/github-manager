function updateSidebarActive() {
    const currentPath = window.location.pathname;
    const items = document.querySelectorAll('.sidebar-item');

    items.forEach(item => {
        const dataPath = item.getAttribute('data-path');
        if (!dataPath) return;

        const isActive = (currentPath === dataPath) || (dataPath === '/' && currentPath === '');

        if (isActive) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });
}

function updateStatsbar() {
    htmx.ajax('GET', '/api/statsbar', {target: '#statsbar', swap: 'outerHTML'});
}

// Re-render only the current page's #content via HTMX instead of a full
// page reload, so the sidebar and statsbar stay put. The htmx:afterSwap
// listener refreshes the statsbar afterwards. Used by the JSON-body
// endpoints (add/remove/sync/save) that can't be driven declaratively.
function refreshContent() {
    if (window.htmx) {
        // Keep the query (e.g. the repositories page's ?group= filter)
        htmx.ajax('GET', window.location.pathname + window.location.search, {target: '#content', swap: 'innerHTML'});
    } else {
        location.reload();
    }
}

// Ensure functions are called after page load
setTimeout(() => {
    updateSidebarActive();
    updateStatsbar();
}, 100);

// Listen for HTMX events globally
document.body.addEventListener('htmx:afterSwap', (event) => {
    setTimeout(() => {
        updateSidebarActive();
        // Prevent recursive statsbar updates - only update if the target wasn't the statsbar itself
        const detail = event?.detail;
        const xhr = detail?.xhr;
        const target = xhr?.responseURL || '';
        if (!target.includes('/api/statsbar')) {
            updateStatsbar();
        }
    }, 50);
});

document.body.addEventListener('htmx:load', () => {
    setTimeout(() => {
        updateSidebarActive();
    }, 50);
});

// Add Repository modal: open/close only. The form itself submits via HTMX
// (see repositories.html) - a 200 swaps #content (modal closes, list refreshes),
// a 400 swaps #errorMessage (modal stays open). One delegated listener, added
// once at load; it survives HTMX swaps and looks elements up fresh each time,
// so there are no stale references or duplicated listeners.
document.addEventListener('click', function (event) {
    const target = event.target;
    if (!target)
        return;

    const modal = document.getElementById('addRepoModal');
    if (!modal)
        return;

    // Open
    if (target.id === 'addRepoBtn') {
        event.preventDefault();
        const err = document.getElementById('errorMessage');
        if (err)
            err.innerHTML = '';
        modal.style.display = 'block';
        const input = document.getElementById('repoOwner');
        if (input)
            input.focus();
        return;
    }

    // Close (X) or click on the backdrop
    if (target.id === 'closeBtn' || target === modal) {
        modal.style.display = 'none';
        const form = document.getElementById('addRepoForm');
        if (form)
            form.reset();
        const err = document.getElementById('errorMessage');
        if (err)
            err.innerHTML = '';
    }
});

// Remove Repository modal: open / confirm / close. Confirm sends DELETE via
// fetch (JSON body) then refreshes #content. Same single-delegated-listener
// pattern - added once, survives swaps, always resolves elements fresh.
let repoToRemove = null;

document.addEventListener('click', function (event) {
    const target = event.target;
    if (!target)
        return;

    const modal = document.getElementById('removeRepoModal');
    if (!modal)
        return;

    // Open from a row's Remove button
    const removeBtn = target.closest ? target.closest('.remove-repo-btn') : null;
    if (removeBtn) {
        event.preventDefault();
        repoToRemove = {
            owner: removeBtn.getAttribute('data-owner'),
            name: removeBtn.getAttribute('data-name')
        };
        const label = document.getElementById('removeRepoName');
        if (label)
            label.textContent = repoToRemove.owner + '/' + repoToRemove.name;
        modal.style.display = 'block';
        return;
    }

    // Close (X or Cancel) or click on the backdrop
    if (target.id === 'removeCloseBtn' || target.id === 'removeCancelBtn' || target === modal) {
        modal.style.display = 'none';
        repoToRemove = null;
        return;
    }

    // Confirm removal
    if (target.id === 'removeConfirmBtn') {
        if (!repoToRemove)
            return;

        fetch('/repositories/remove', {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(repoToRemove)
        })
            .then(response => {
                if (response.ok) {
                    modal.style.display = 'none';
                    repoToRemove = null;
                    refreshContent();
                } else {
                    alert('Error removing repository');
                }
            })
            .catch(error => {
                console.error('Error:', error);
                alert('Error removing repository');
            });
    }
});

// Create Group modal (repositories page): open/close only; the form submits
// via HTMX like the add-repo form (200 swaps #content, 400 shows the reason).
document.addEventListener('click', function (event) {
    const target = event.target;
    if (!target)
        return;

    const modal = document.getElementById('addGroupModal');
    if (!modal)
        return;

    if (target.id === 'addGroupBtn') {
        event.preventDefault();
        const err = document.getElementById('groupErrorMessage');
        if (err)
            err.innerHTML = '';
        modal.style.display = 'block';
        const input = document.getElementById('groupName');
        if (input)
            input.focus();
        return;
    }

    if (target.id === 'addGroupCloseBtn' || target === modal) {
        modal.style.display = 'none';
        const form = document.getElementById('addGroupForm');
        if (form)
            form.reset();
    }
});

// Edit Repository modal (repositories page): a row's Edit button fills it
// with the repository and checks the groups it is in (data-groups, comma
// separated ids). Save submits via HTMX; Remove hands over to the remove
// modal (its button carries the .remove-repo-btn class and the repository).
document.addEventListener('click', function (event) {
    const target = event.target;
    if (!target)
        return;

    const modal = document.getElementById('editRepoModal');
    if (!modal)
        return;

    const editBtn = target.closest ? target.closest('.edit-repo-btn') : null;
    if (editBtn) {
        event.preventDefault();
        const owner = editBtn.getAttribute('data-owner');
        const name = editBtn.getAttribute('data-name');
        const groups = (editBtn.getAttribute('data-groups') || '').split(',').filter(Boolean);
        document.getElementById('editRepoOwner').value = owner;
        document.getElementById('editRepoRepoName').value = name;
        document.getElementById('editRepoName').textContent = owner + '/' + name;
        document.querySelectorAll('.edit-group-check').forEach(function (check) {
            check.checked = groups.indexOf(check.value) >= 0;
        });
        const removeBtn = document.getElementById('editRemoveBtn');
        removeBtn.setAttribute('data-owner', owner);
        removeBtn.setAttribute('data-name', name);
        const err = document.getElementById('editErrorMessage');
        if (err)
            err.innerHTML = '';
        modal.style.display = 'block';
        return;
    }

    // Remove: close this modal; the remove modal's own listener opens it
    if (target.id === 'editRemoveBtn' || target.id === 'editCloseBtn' || target === modal)
        modal.style.display = 'none';
});

// Per-row Refresh: sync a single repository.
// One delegated listener added at load time; it survives HTMX swaps and handles
// dynamically rendered rows, so it does not need re-initialization.
document.addEventListener('click', function (event) {
    const btn = event.target.closest ? event.target.closest('.refresh-repo-btn') : null;
    if (!btn)
        return;

    event.preventDefault();

    const syncUrl = btn.getAttribute('data-sync-url') || '/repositories/sync';
    const payload = {
        owner: btn.getAttribute('data-owner'),
        name: btn.getAttribute('data-name')
    };

    fetch(syncUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
    })
        .then(response => {
            if (response.ok) {
                refreshContent();
            } else {
                alert('Error refreshing repository');
            }
        })
        .catch(error => {
            console.error('Error:', error);
            alert('Error refreshing repository');
        });
});

// Header Sync: refresh all repositories. The target endpoint is read from the
// button's data-sync-url so different pages can post to their own sync route.
// Sent with no body so an @RequestBody(required=false) resolves to null (full sync).
document.addEventListener('click', function (event) {
    if (!event.target || event.target.id !== 'syncBtn')
        return;

    event.preventDefault();

    const syncUrl = event.target.getAttribute('data-sync-url') || '/repositories/sync';

    fetch(syncUrl, {
        method: 'POST'
    })
        .then(response => {
            if (response.ok) {
                refreshContent();
            } else {
                alert('Error syncing repositories');
            }
        })
        .catch(error => {
            console.error('Error:', error);
            alert('Error syncing repositories');
        });
});

// Workflows: show/hide the create-workflow builder.
document.addEventListener('click', function (event) {
    if (!event.target)
        return;

    if (event.target.id === 'createWorkflowBtn') {
        const builder = document.getElementById('workflowBuilder');
        if (builder) {
            wfResetBuilderForCreate();
            builder.style.display = 'block';
        }
        return;
    }

    if (event.target.id === 'closeWorkflowBuilderBtn') {
        const builder = document.getElementById('workflowBuilder');
        if (builder)
            builder.style.display = 'none';
        return;
    }

    // Modify: load an existing workflow's name and stages into the builder,
    // exactly as if it were being created fresh.
    if (event.target.classList && event.target.classList.contains('workflow-edit-btn')) {
        const card = event.target.closest('.workflow-card');
        if (card)
            wfLoadWorkflowIntoBuilder(card);
        return;
    }
});

// Start-workflow modal: open / cancel / confirm.
// Opening reads the clicked workflow card's stages and renders one row per
// stage into the modal, asking for the runtime values (branch, and for a
// Maven bump the version part) that may have been left undecided at creation.
let wfStartWorkflowName = null;

document.addEventListener('click', function (event) {
    const target = event.target;
    if (!target)
        return;

    const modal = document.getElementById('startWorkflowModal');
    if (!modal)
        return;

    // Open from a workflow card's Start button
    const startBtn = target.closest ? target.closest('.workflow-start-btn') : null;
    if (startBtn) {
        event.preventDefault();
        const card = startBtn.closest('.workflow-card');
        wfStartWorkflowName = startBtn.getAttribute('data-workflow-name');
        wfPopulateStartModal(card, wfStartWorkflowName);
        modal.style.display = 'block';
        return;
    }

    // Close (X / Cancel) or click on the backdrop
    if (target.id === 'startWorkflowCloseBtn' || target.id === 'startWorkflowCancelBtn' || target === modal) {
        modal.style.display = 'none';
        wfStartWorkflowName = null;
        return;
    }

    // Confirm: start the workflow, sending the per-stage runtime values as a JSON
    // body — the same way the create/edit builder submits a workflow.
    if (target.id === 'startWorkflowConfirmBtn') {
        if (!wfStartWorkflowName)
            return;

        const runtimeStages = wfCollectStartValues();

        fetch('/workflows/start', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'HX-Request': 'true'
            },
            body: JSON.stringify({name: wfStartWorkflowName, stages: runtimeStages})
        })
            .then(response => {
                if (response.ok) {
                    modal.style.display = 'none';
                    wfStartWorkflowName = null;
                    refreshContent();
                } else {
                    alert('Error starting workflow');
                }
            })
            .catch(error => {
                console.error('Error:', error);
                alert('Error starting workflow');
            });
        return;
    }
});

// Returns the branch names configured for a given "owner/name" repository,
// read from the hidden #wfBranchSource block rendered on the page.
function wfBranchesForRepo(repoKey) {
    const source = document.getElementById('wfBranchSource');
    if (!source)
        return [];

    const repo = source.querySelector('.wf-branch-repo[data-repo="' + repoKey + '"]');
    if (!repo)
        return [];

    return Array.prototype.map.call(repo.querySelectorAll('span[data-branch]'), function (span) {
        return span.getAttribute('data-branch');
    });
}

// Builds the start modal's body from a workflow card, one row per stage.
function wfPopulateStartModal(card, workflowName) {
    const title = document.getElementById('startWorkflowTitle');
    const container = document.getElementById('startWorkflowStages');
    if (!container)
        return;

    if (title)
        title.textContent = 'Start workflow: ' + (workflowName || '');

    container.innerHTML = '';
    if (!card)
        return;

    Array.prototype.forEach.call(card.querySelectorAll('.workflow-card-stage'), function (li, i) {
        container.appendChild(wfBuildStartStageRow(li.dataset, i + 1));
    });
}

// Wraps a control in a labelled .wf-field, optionally with a hint line below.
function wfStartField(labelText, control, hintText) {
    const field = document.createElement('div');
    field.className = 'wf-field';

    const label = document.createElement('label');
    label.textContent = labelText;
    field.appendChild(label);
    field.appendChild(control);

    if (hintText) {
        const hint = document.createElement('div');
        hint.className = 'wf-start-hint';
        hint.textContent = hintText;
        field.appendChild(hint);
    }
    return field;
}

// Builds a branch <select> for a repository, pre-selecting the configured value.
function wfStartBranchSelect(repoKey, configured) {
    const select = document.createElement('select');
    select.className = 'form-control wf-start-branch';

    const branches = wfBranchesForRepo(repoKey);

    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Select a branch...';
    select.appendChild(placeholder);

    // Keep a configured branch selectable even if it is not in the fetched list.
    if (configured && branches.indexOf(configured) === -1)
        branches.unshift(configured);

    branches.forEach(function (name) {
        const option = document.createElement('option');
        option.value = name;
        option.textContent = name;
        if (name === configured)
            option.selected = true;
        select.appendChild(option);
    });
    return select;
}

// Builds a text input pre-filled with a value.
function wfStartInput(className, value, placeholder) {
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'form-control ' + className;
    if (value)
        input.value = value;
    if (placeholder)
        input.placeholder = placeholder;
    return input;
}

// Builds a multi-line textarea pre-filled with a value.
function wfStartTextarea(className, value, placeholder) {
    const textarea = document.createElement('textarea');
    textarea.className = 'form-control ' + className;
    textarea.rows = 4;
    if (value)
        textarea.value = value;
    if (placeholder)
        textarea.placeholder = placeholder;
    return textarea;
}

// Builds a segmented button group (styled like the run-mode toggle) for the
// Maven version part. The existing .wf-runmode-btn click handler toggles the
// active button within the group; the active one is read back on confirm.
function wfStartVersionPartButtons(defaultValue) {
    const group = document.createElement('div');
    group.className = 'wf-runmode wf-start-version-part';

    [['PATCH', 'Patch'], ['MINOR', 'Minor'], ['MAJOR', 'Major']].forEach(function (pair) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'wf-runmode-btn';
        btn.dataset.value = pair[0];
        btn.textContent = pair[1];
        if (pair[0] === (defaultValue || 'PATCH'))
            btn.classList.add('active');
        group.appendChild(btn);
    });
    return group;
}

// Builds a single stage row for the start modal from a stage's data-* set.
// Each stage shows every field creation offered, pre-filled with the saved
// value when one exists, so any of them can be adjusted before starting.
function wfBuildStartStageRow(d, index) {
    const repoKey = (d.repoOwner || '') + '/' + (d.repoName || '');
    const isCommit = d.task === 'COMMIT';

    const row = document.createElement('div');
    row.className = 'wf-start-stage';
    row.dataset.index = index;
    // Stash the full saved stage so confirm can send a complete DTO stage,
    // overlaying only the fields the dialog lets the user edit.
    row.dataset.base = JSON.stringify({
        repository: {owner: d.repoOwner || null, name: d.repoName || null},
        task: d.task || null,
        commitOperation: d.commitOperation || null,
        targetBranch: d.targetBranch || null,
        commitFilePath: d.commitFilePath || null,
        commitFindRegex: d.commitFindRegex || null,
        commitContent: d.commitContent || null,
        commitMessage: d.commitMessage || null,
        versionPart: d.versionPart || null,
        workflowUrl: d.workflowUrl || null,
        runMode: d.runMode || null
    });

    // Header: index + stage summary, mirroring the card's stage description.
    const head = document.createElement('div');
    head.className = 'wf-start-stage-head';

    const badge = document.createElement('span');
    badge.className = 'wf-stage-index';
    badge.textContent = index;

    const text = document.createElement('div');
    const title = document.createElement('div');
    title.className = 'wf-stage-title';
    title.textContent = repoKey + ' — ' + (isCommit ? 'Commit' : 'Pipeline');

    const detail = document.createElement('div');
    detail.className = 'wf-stage-detail';
    if (isCommit) {
        if (d.commitOperation === 'BUMP_MAVEN_VERSION')
            detail.textContent = 'bump Maven version' + (d.versionPart ? ' (' + d.versionPart.toLowerCase() + ')' : '');
        else if (d.commitOperation === 'FIND_AND_REPLACE_REGEX')
            detail.textContent = 'find & replace (regex)';
        else if (d.commitOperation === 'SET_FILE_CONTENT')
            detail.textContent = 'set file content';
    } else {
        detail.textContent = 'pipeline: ' + (d.workflowUrl || '');
    }

    text.appendChild(title);
    text.appendChild(detail);
    head.appendChild(badge);
    head.appendChild(text);
    row.appendChild(head);

    // Runtime fields
    const fields = document.createElement('div');
    fields.className = 'wf-start-stage-fields';

    // Branch: applies to both commit and pipeline stages.
    const configured = d.targetBranch || '';
    fields.appendChild(wfStartField(
        'Branch',
        wfStartBranchSelect(repoKey, configured),
        configured
            ? 'Set to "' + configured + '" when the workflow was created — change it if needed.'
            : 'Not set at creation — choose the branch to run on.'
    ));

    if (isCommit) {
        if (d.commitOperation === 'BUMP_MAVEN_VERSION') {
            fields.appendChild(wfStartField('Version part to bump', wfStartVersionPartButtons(d.versionPart || 'PATCH')));
            fields.appendChild(wfStartField('Commit message',
                wfStartInput('wf-start-message', d.commitMessage, 'e.g. chore: bump version')));
        } else if (d.commitOperation === 'FIND_AND_REPLACE_REGEX') {
            fields.appendChild(wfStartField('File path',
                wfStartInput('wf-start-file', d.commitFilePath, 'path/to/file')));
            fields.appendChild(wfStartField('Pattern (regex)',
                wfStartInput('wf-start-pattern', d.commitFindRegex, '<version>(.*)</version>')));
            fields.appendChild(wfStartField('Replacement',
                wfStartInput('wf-start-content', d.commitContent, '<version>1.4.0</version>')));
            fields.appendChild(wfStartField('Commit message',
                wfStartInput('wf-start-message', d.commitMessage, 'e.g. chore: bump version')));
        } else if (d.commitOperation === 'SET_FILE_CONTENT') {
            fields.appendChild(wfStartField('File path',
                wfStartInput('wf-start-file', d.commitFilePath, 'path/to/file')));
            fields.appendChild(wfStartField('Content',
                wfStartTextarea('wf-start-content', d.commitContent, 'New file content')));
            fields.appendChild(wfStartField('Commit message',
                wfStartInput('wf-start-message', d.commitMessage, 'e.g. chore: update file')));
        }
    }

    row.appendChild(fields);
    return row;
}

// Collects the stages from the start modal as complete, DTO-shaped stage objects
// (the same shape the create/edit builder submits), so the start request is an
// InternalWorkflowDTO. Each stage starts from the saved definition and overlays
// only the fields the dialog exposes; fields not shown keep their saved value.
function wfCollectStartValues() {
    const container = document.getElementById('startWorkflowStages');
    if (!container)
        return [];

    return Array.prototype.map.call(container.querySelectorAll('.wf-start-stage'), function (row) {
        const base = JSON.parse(row.dataset.base || '{}');

        // undefined => the field isn't shown for this stage, so keep the saved value.
        const edited = function (selector) {
            const el = row.querySelector(selector);
            return el ? (el.value.trim() || null) : undefined;
        };
        const branch = edited('.wf-start-branch');
        const file = edited('.wf-start-file');
        const pattern = edited('.wf-start-pattern');
        const content = edited('.wf-start-content');
        const message = edited('.wf-start-message');
        const activePart = row.querySelector('.wf-start-version-part .wf-runmode-btn.active');

        return {
            repository: base.repository,
            task: base.task,
            commitOperation: base.commitOperation,
            targetBranch: branch !== undefined ? branch : (base.targetBranch || null),
            commitFilePath: file !== undefined ? file : (base.commitFilePath || null),
            commitFindRegex: pattern !== undefined ? pattern : (base.commitFindRegex || null),
            commitContent: content !== undefined ? content : (base.commitContent || null),
            commitMessage: message !== undefined ? message : (base.commitMessage || null),
            versionPart: activePart ? activePart.dataset.value : (base.versionPart || null),
            workflowUrl: base.workflowUrl || null,
            runMode: base.runMode || null
        };
    });
}

// Resets the builder to a clean "create new workflow" state.
function wfResetBuilderForCreate() {
    const builder = document.getElementById('workflowBuilder');
    const list = document.getElementById('wfStageList');
    const nameEl = document.getElementById('wfName');

    if (list)
        list.innerHTML = '';
    if (nameEl) {
        nameEl.value = '';
        nameEl.readOnly = false;
    }
    if (builder) {
        builder.dataset.mode = 'create';
        delete builder.dataset.originalName;
    }

    wfResetEditor();
    wfUpdateRunModeVisibility();
    wfSetBuilderMode('create');
}

// Switches the builder's title and primary-button label between create and
// edit, so the user can tell which action Save will perform.
function wfSetBuilderMode(mode) {
    const title = document.querySelector('#workflowBuilder .panel-title');
    const saveBtn = document.getElementById('wfSaveBtn');
    if (title)
        title.textContent = (mode === 'edit') ? 'Modify workflow' : 'New workflow';
    if (saveBtn)
        saveBtn.textContent = (mode === 'edit') ? 'Update workflow' : 'Save workflow';
}

// Rebuilds the builder from an existing workflow card. Reads the per-stage
// data-* attributes rendered on each .workflow-card-stage and recreates the
// stage list. Saving in this mode PATCHes /workflows/update.
function wfLoadWorkflowIntoBuilder(card) {
    const builder = document.getElementById('workflowBuilder');
    const list = document.getElementById('wfStageList');
    if (!builder || !list)
        return;

    // Start from a clean editor and an empty stage list.
    wfResetEditor();
    list.innerHTML = '';

    const nameEl = document.getElementById('wfName');
    const cardName = card.querySelector('.workflow-name');
    const name = cardName ? cardName.textContent.trim() : '';
    if (nameEl) {
        nameEl.value = name;
        // The backend matches the workflow to update by name, so the name is
        // locked while editing - changing it would create/miss instead of update.
        nameEl.readOnly = true;
    }

    Array.prototype.forEach.call(card.querySelectorAll('.workflow-card-stage'), function (li, i) {
        const d = li.dataset;
        const stage = {
            repository: {owner: d.repoOwner, name: d.repoName},
            task: d.task,
            commitOperation: d.commitOperation || null,
            targetBranch: d.targetBranch || null,
            commitFilePath: d.commitFilePath || null,
            commitFindRegex: d.commitFindRegex || null,
            commitContent: d.commitContent || null,
            commitMessage: d.commitMessage || null,
            versionPart: d.versionPart || null,
            workflowUrl: d.workflowUrl || null,
            runMode: d.runMode || null
        };
        list.appendChild(wfBuildStageItem(stage, i + 1));
    });

    builder.dataset.mode = 'edit';
    builder.dataset.originalName = name;
    wfSetBuilderMode('edit');

    wfRenumberStages();
    wfUpdateRunModeVisibility();

    builder.style.display = 'block';
    builder.scrollIntoView({behavior: 'smooth', block: 'start'});
}

// Workflow builder: stage editor logic.
// The added-stages DOM list is the source of truth, so state does not leak
// across HTMX page navigations.

function wfTaskFieldsToggle() {
    const task = document.getElementById('wfTask');
    if (!task)
        return;

    const commit = document.getElementById('wfCommitFields');
    const pipeline = document.getElementById('wfPipelineFields');
    const branch = document.getElementById('wfBranchField');
    if (commit)
        commit.style.display = (task.value === 'COMMIT') ? 'flex' : 'none';
    if (pipeline)
        pipeline.style.display = (task.value === 'WORKFLOW') ? 'flex' : 'none';
    // Target branch is relevant to both tasks, so it shows for either.
    if (branch)
        branch.style.display = task.value ? 'flex' : 'none';

    if (task.value === 'COMMIT')
        wfCommitOpToggle();
    if (task.value === 'WORKFLOW')
        wfPipelineFilter();
}

// Restricts the pipeline dropdown to the workflows registered on the
// currently selected repository. Options are physically removed/re-added
// (rather than just hidden) because the `hidden` attribute on <option>
// elements is not honored by every browser's native dropdown rendering.
let wfPipelineOptionsCache = null;

function wfPipelineFilter() {
    const repo = document.getElementById('wfRepo');
    const pipeline = document.getElementById('wfPipeline');
    if (!repo || !pipeline)
        return;

    // Cache the full, unfiltered option list once per DOM instance of the
    // select (HTMX page swaps create a fresh element, so the cache is
    // rebuilt whenever the element reference changes).
    if (!wfPipelineOptionsCache || wfPipelineOptionsCache.element !== pipeline) {
        wfPipelineOptionsCache = {
            element: pipeline,
            options: Array.prototype.filter.call(pipeline.options, function (option) {
                return !!option.value;
            })
        };
    }

    const previousValue = pipeline.value;

    Array.prototype.slice.call(pipeline.options).forEach(function (option) {
        if (option.value)
            option.remove();
    });

    wfPipelineOptionsCache.options
        .filter(function (option) {
            return option.dataset.repo === repo.value;
        })
        .forEach(function (option) {
            pipeline.appendChild(option.cloneNode(true));
        });

    pipeline.value = Array.prototype.some.call(pipeline.options, function (o) {
        return o.value === previousValue;
    }) ? previousValue : '';
}

// Reveals the fields for the currently selected commit operation
function wfCommitOpToggle() {
    const op = document.getElementById('wfCommitOp');
    if (!op)
        return;

    const isBump = op.value === 'BUMP_MAVEN_VERSION';

    // Maven version bump asks for nothing else - file, message and the amount
    // are all decided when the workflow runs.
    const fileField = document.getElementById('wfCommitFileField');
    const messageField = document.getElementById('wfCommitMessageField');
    const findReplace = document.getElementById('wfFindReplaceFields');
    const setContent = document.getElementById('wfSetContentFields');

    if (fileField)
        fileField.style.display = isBump ? 'none' : 'flex';
    if (messageField)
        messageField.style.display = isBump ? 'none' : 'flex';
    if (findReplace)
        findReplace.style.display = (op.value === 'FIND_AND_REPLACE_REGEX') ? 'flex' : 'none';
    if (setContent)
        setContent.style.display = (op.value === 'SET_FILE_CONTENT') ? 'flex' : 'none';

    // The version-part picker only applies to a Maven bump.
    const bumpFields = document.getElementById('wfBumpFields');
    if (bumpFields)
        bumpFields.style.display = isBump ? 'flex' : 'none';
}

function wfUpdateRunModeVisibility() {
    const list = document.getElementById('wfStageList');
    const field = document.getElementById('wfRunModeField');
    if (!list || !field)
        return;

    field.style.display = list.children.length > 0 ? 'flex' : 'none';
}

function wfResetEditor() {
    ['wfRepo', 'wfTask', 'wfPipeline', 'wfTargetBranch', 'wfCommitFile',
        'wfFrPattern', 'wfFrReplacement', 'wfScContent', 'wfCommitMessage'].forEach(function (id) {
        const el = document.getElementById(id);
        if (el)
            el.value = '';
    });

    const op = document.getElementById('wfCommitOp');
    if (op)
        op.value = 'BUMP_MAVEN_VERSION';

    wfTaskFieldsToggle();
    wfPipelineFilter();

    const group = document.getElementById('wfRunMode');
    if (group) {
        Array.prototype.forEach.call(group.querySelectorAll('.wf-runmode-btn'), function (btn, i) {
            btn.classList.toggle('active', i === 0);
        });
    }

    // Reset the Maven version-part picker to its default (Patch).
    const versionGroup = document.getElementById('wfVersionPart');
    if (versionGroup) {
        Array.prototype.forEach.call(versionGroup.querySelectorAll('.wf-runmode-btn'), function (btn) {
            btn.classList.toggle('active', btn.dataset.value === 'PATCH');
        });
    }
}

function wfRenumberStages() {
    const list = document.getElementById('wfStageList');
    if (!list)
        return;

    Array.prototype.forEach.call(list.children, function (item, i) {
        const idx = item.querySelector('.wf-stage-index');
        if (idx)
            idx.textContent = (i + 1);
    });
}

function wfBuildStageItem(stage, index) {
    const li = document.createElement('li');
    li.className = 'wf-stage-item';

    // Only set dataset entries that have a value, so wfCollectStages can
    // rebuild the exact same (DTO-shaped, null-omitting) stage object later.
    const setData = function (key, value) {
        if (value !== null && value !== undefined && value !== '')
            li.dataset[key] = value;
    };

    setData('repoOwner', stage.repository.owner);
    setData('repoName', stage.repository.name);
    setData('task', stage.task);
    setData('commitOperation', stage.commitOperation);
    setData('targetBranch', stage.targetBranch);
    setData('commitFilePath', stage.commitFilePath);
    setData('commitFindRegex', stage.commitFindRegex);
    setData('commitContent', stage.commitContent);
    setData('commitMessage', stage.commitMessage);
    setData('versionPart', stage.versionPart);
    setData('workflowUrl', stage.workflowUrl);
    setData('runMode', stage.runMode);

    const summary = document.createElement('div');
    summary.className = 'wf-stage-summary';

    const badge = document.createElement('span');
    badge.className = 'wf-stage-index';
    badge.textContent = index;

    const text = document.createElement('div');

    const repoLabel = stage.repository.owner + '/' + stage.repository.name;

    const title = document.createElement('div');
    title.className = 'wf-stage-title';
    title.textContent = repoLabel + ' - ' + (stage.task === 'COMMIT' ? 'Commit' : 'Pipeline');

    const detail = document.createElement('div');
    detail.className = 'wf-stage-detail';
    let detailText;
    if (stage.task === 'COMMIT') {
        if (stage.commitOperation === 'BUMP_MAVEN_VERSION')
            detailText = 'bump Maven version' + (stage.versionPart ? ' (' + stage.versionPart.toLowerCase() + ')' : '');
        else if (stage.commitOperation === 'FIND_AND_REPLACE_REGEX')
            detailText = 'replace /' + stage.commitFindRegex + '/ in ' + stage.commitFilePath;
        else
            detailText = 'set content of ' + stage.commitFilePath;
    } else {
        detailText = 'pipeline: ' + stage.workflowUrl;
    }
    if (stage.targetBranch) {
        detailText += ' on ' + stage.targetBranch;
    }
    if (stage.runMode)
        detailText += ' · ' + (stage.runMode === 'WITH_PREVIOUS' ? 'with previous' : 'after previous');
    detail.textContent = detailText;

    const handle = document.createElement('span');
    handle.className = 'wf-stage-drag';
    handle.title = 'Drag to reorder';
    handle.textContent = '☰';

    text.appendChild(title);
    text.appendChild(detail);
    summary.appendChild(handle);
    summary.appendChild(badge);
    summary.appendChild(text);

    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'btn btn-danger wf-stage-remove';
    remove.textContent = 'Remove';

    li.appendChild(summary);
    li.appendChild(remove);
    return li;
}

function wfCollectStages() {
    const list = document.getElementById('wfStageList');
    if (!list)
        return [];

    return Array.prototype.map.call(list.children, function (item) {
        const d = item.dataset;
        return {
            repository: {owner: d.repoOwner, name: d.repoName},
            task: d.task,
            commitOperation: d.commitOperation || null,
            targetBranch: d.targetBranch || null,
            commitFilePath: d.commitFilePath || null,
            commitFindRegex: d.commitFindRegex || null,
            commitContent: d.commitContent || null,
            commitMessage: d.commitMessage || null,
            versionPart: d.versionPart || null,
            workflowUrl: d.workflowUrl || null,
            runMode: d.runMode || null
        };
    });
}

// Drag-and-drop reordering of stages via the ☰ handle.
// Dragging is enabled only while the pointer starts on the handle.
let wfDraggingItem = null;

document.addEventListener('mousedown', function (event) {
    if (event.target && event.target.classList && event.target.classList.contains('wf-stage-drag')) {
        const item = event.target.closest('.wf-stage-item');
        if (item)
            item.draggable = true;
    }
});

document.addEventListener('mouseup', function () {
    // Clear any stray draggable flag when a click on the handle didn't turn into a drag
    if (wfDraggingItem)
        return;
    const list = document.getElementById('wfStageList');
    if (!list)
        return;
    Array.prototype.forEach.call(list.querySelectorAll('.wf-stage-item'), function (item) {
        item.draggable = false;
    });
});

document.addEventListener('dragstart', function (event) {
    const item = event.target.closest ? event.target.closest('.wf-stage-item') : null;
    if (!item || !item.draggable)
        return;

    wfDraggingItem = item;
    item.classList.add('wf-dragging');
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', ''); // required for Firefox to start a drag
    }
});

document.addEventListener('dragover', function (event) {
    if (!wfDraggingItem)
        return;
    const list = document.getElementById('wfStageList');
    if (!list || !list.contains(event.target))
        return;

    event.preventDefault();
    const overItem = event.target.closest('.wf-stage-item');
    if (!overItem || overItem === wfDraggingItem)
        return;

    const rect = overItem.getBoundingClientRect();
    const after = (event.clientY - rect.top) > (rect.height / 2);
    if (after)
        overItem.after(wfDraggingItem);
    else
        overItem.before(wfDraggingItem);
});

document.addEventListener('drop', function (event) {
    if (!wfDraggingItem)
        return;
    const list = document.getElementById('wfStageList');
    if (list && list.contains(event.target))
        event.preventDefault();
});

document.addEventListener('dragend', function () {
    if (!wfDraggingItem)
        return;
    wfDraggingItem.classList.remove('wf-dragging');
    wfDraggingItem.draggable = false;
    wfDraggingItem = null;
    wfRenumberStages();
});

// Dashboard Repository Lookup: live filter by name
document.addEventListener('input', function (event) {
    if (!event.target || event.target.id !== 'repoSearch')
        return;

    const q = event.target.value.trim().toLowerCase();
    const groups = document.querySelectorAll('.lk-group');
    let shown = 0;
    Array.prototype.forEach.call(groups, function (group) {
        const name = group.getAttribute('data-name') || '';
        const match = name.indexOf(q) !== -1;
        group.style.display = match ? '' : 'none';
        if (match)
            shown++;
    });

    const shownEl = document.getElementById('lkShown');
    if (shownEl)
        shownEl.textContent = shown;

    const noMatch = document.getElementById('lkNoMatch');
    if (noMatch)
        noMatch.hidden = !(groups.length > 0 && shown === 0);
});

// Dashboard Repository Lookup: expand/collapse a repository row
document.addEventListener('click', function (event) {
    const row = event.target.closest ? event.target.closest('.lk-row') : null;
    if (!row)
        return;
    if (event.target.closest('a'))
        return;

    const detail = row.nextElementSibling;
    if (detail && detail.classList.contains('lk-detail'))
        detail.hidden = !detail.hidden;

    const chevron = row.querySelector('.lk-chevron');
    if (chevron)
        chevron.textContent = (detail && !detail.hidden) ? '▾' : '▸';
});

// Dashboard Repository Lookup: switch tabs within an expanded row.
// One delegated listener; the active tab and its panel are scoped to the
// enclosing .lk-detail so each repository's tabs are independent.
document.addEventListener('click', function (event) {
    const tab = event.target.closest ? event.target.closest('.lk-tab') : null;
    if (!tab)
        return;

    const detail = tab.closest('.lk-detail');
    if (!detail)
        return;

    const name = tab.getAttribute('data-tab');
    Array.prototype.forEach.call(detail.querySelectorAll('.lk-tab'), function (t) {
        t.classList.toggle('active', t === tab);
    });
    Array.prototype.forEach.call(detail.querySelectorAll('.lk-tabpanel'), function (panel) {
        panel.hidden = (panel.getAttribute('data-panel') !== name);
    });
});

// Task dropdown reveals its matching fields
document.addEventListener('change', function (event) {
    if (event.target && event.target.id === 'wfTask')
        wfTaskFieldsToggle();
    if (event.target && event.target.id === 'wfCommitOp')
        wfCommitOpToggle();
    if (event.target && event.target.id === 'wfRepo')
        wfPipelineFilter();
});

document.addEventListener('click', function (event) {
    const target = event.target;
    if (!target)
        return;

    // Run-mode toggle (after / with previous)
    if (target.classList && target.classList.contains('wf-runmode-btn')) {
        const group = target.parentElement;
        if (group) {
            Array.prototype.forEach.call(group.querySelectorAll('.wf-runmode-btn'), function (btn) {
                btn.classList.remove('active');
            });
        }
        target.classList.add('active');
        return;
    }

    // Finish (+) a stage: validate, append to the list, reset the editor
    if (target.id === 'wfAddStageBtn') {
        const repo = document.getElementById('wfRepo');
        const task = document.getElementById('wfTask');
        const list = document.getElementById('wfStageList');
        if (!repo || !task || !list)
            return;

        if (!repo.value) {
            alert('Select a repository for this stage.');
            return;
        }
        if (!task.value) {
            alert('Select a task for this stage.');
            return;
        }

        const isFirst = list.children.length === 0;
        const activeRunBtn = document.querySelector('#wfRunMode .wf-runmode-btn.active');
        const slashIndex = repo.value.indexOf('/');
        const stage = {
            repository: {
                owner: repo.value.substring(0, slashIndex),
                name: repo.value.substring(slashIndex + 1)
            },
            task: task.value,
            commitOperation: null,
            targetBranch: null,
            commitFilePath: null,
            commitFindRegex: null,
            commitContent: null,
            commitMessage: null,
            versionPart: null,
            workflowUrl: null,
            runMode: isFirst ? null : (activeRunBtn ? activeRunBtn.dataset.value : 'AFTER_PREVIOUS_FINISHES')
        };

        // Target branch applies to both Commit and Pipeline tasks.
        const branch = document.getElementById('wfTargetBranch');
        stage.targetBranch = branch ? branch.value.trim() || null : null;

        if (task.value === 'COMMIT') {
            const op = document.getElementById('wfCommitOp');
            const file = document.getElementById('wfCommitFile');
            const message = document.getElementById('wfCommitMessage');

            stage.commitOperation = op ? op.value || null : null;
            stage.commitMessage = message ? message.value.trim() || null : null;
            const filePath = file ? file.value.trim() : '';

            // bump-version asks only for the version part (defaulting to Patch).
            if (stage.commitOperation === 'BUMP_MAVEN_VERSION') {
                const activeVersionBtn = document.querySelector('#wfVersionPart .wf-runmode-btn.active');
                stage.versionPart = activeVersionBtn ? activeVersionBtn.dataset.value : 'PATCH';
            } else if (stage.commitOperation === 'FIND_AND_REPLACE_REGEX') {
                const pattern = document.getElementById('wfFrPattern');
                const replacement = document.getElementById('wfFrReplacement');
                if (!filePath) {
                    alert('Enter the file path.');
                    return;
                }
                if (!pattern || !pattern.value.trim()) {
                    alert('Enter the search pattern.');
                    return;
                }
                stage.commitFilePath = filePath;
                stage.commitFindRegex = pattern.value.trim();
                // The DTO has no dedicated "replacement" field, so the
                // replacement text is carried in commitContent.
                stage.commitContent = replacement ? (replacement.value || null) : null;
            } else if (stage.commitOperation === 'SET_FILE_CONTENT') {
                const content = document.getElementById('wfScContent');
                if (!filePath) {
                    alert('Enter the file path.');
                    return;
                }
                if (!content || !content.value.trim()) {
                    alert('Enter the file content.');
                    return;
                }
                stage.commitFilePath = filePath;
                stage.commitContent = content.value;
            }
        } else if (task.value === 'WORKFLOW') {
            const pipeline = document.getElementById('wfPipeline');
            if (!pipeline || !pipeline.value) {
                alert('Select a pipeline for this stage.');
                return;
            }
            stage.workflowUrl = pipeline.value;
        }

        list.appendChild(wfBuildStageItem(stage, list.children.length + 1));
        wfRenumberStages();
        wfResetEditor();
        wfUpdateRunModeVisibility();
        return;
    }

    // Remove an added stage
    if (target.classList && target.classList.contains('wf-stage-remove')) {
        const item = target.closest('.wf-stage-item');
        if (item)
            item.remove();
        wfRenumberStages();
        wfUpdateRunModeVisibility();
        return;
    }

    // Save the workflow. In create mode: POST /workflows/save (JSON response).
    // In edit mode: PATCH /workflows/update (HTML fragment response). Either
    // way, refresh only #content so the sidebar and statsbar are preserved.
    if (target.id === 'wfSaveBtn') {
        const builder = document.getElementById('workflowBuilder');
        const editMode = !!(builder && builder.dataset.mode === 'edit');
        const nameEl = document.getElementById('wfName');
        const name = nameEl ? nameEl.value.trim() : '';
        const stages = wfCollectStages();

        if (!name) {
            alert('Enter a workflow name.');
            return;
        }
        if (stages.length === 0) {
            alert('Add at least one stage before saving.');
            return;
        }

        const url = editMode ? '/workflows/update' : '/workflows/save';
        const method = editMode ? 'PATCH' : 'POST';

        fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                'HX-Request': 'true'
            },
            body: JSON.stringify({name: name, stages: stages})
        })
            .then(response => {
                const contentType = response.headers.get('content-type') || '';
                // /save returns JSON {success,...}; /update returns an HTML fragment.
                if (contentType.includes('application/json'))
                    return response.json().then(data => ({ok: response.ok, data: data}));
                return {ok: response.ok, data: {success: response.ok}};
            })
            .then(({ok, data}) => {
                if (ok && data.success) {
                    refreshContent();
                } else {
                    alert(data.error || 'Error saving workflow');
                }
            })
            .catch(error => {
                console.error('Error:', error);
                alert('Error saving workflow');
            });
        return;
    }
});
