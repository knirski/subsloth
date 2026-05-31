import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;

const databases = new Map();
const statements = new Map();
let nextDatabaseId = 0;
let nextStatementId = 0;

function openRequest(id, requestData) {
  try {
    const newDatabaseId = nextDatabaseId++;
    const newDatabase = typeof sqlite3.oo1.OpfsDb !== 'undefined'
      ? new sqlite3.oo1.OpfsDb(requestData.fileName)
      : new sqlite3.oo1.DB(requestData.fileName, 'ct');
    databases.set(newDatabaseId, newDatabase);
    postMessage({ id, data: { databaseId: newDatabaseId } });
  } catch (error) {
    postMessage({ id, error: error.message });
  }
}

function prepareRequest(id, requestData) {
  try {
    const newStatementId = nextStatementId++;
    const database = databases.get(requestData.databaseId);
    if (!database) {
      postMessage({ id, error: 'Invalid database ID: ' + requestData.databaseId });
      return;
    }
    const statement = database.prepare(requestData.sql);
    statements.set(newStatementId, statement);
    const columnNames = [];
    for (let i = 0; i < statement.columnCount; i++) {
      columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
    }
    postMessage({
      id,
      data: {
        statementId: newStatementId,
        parameterCount: sqlite3.capi.sqlite3_bind_parameter_count(statement),
        columnNames,
      },
    });
  } catch (error) {
    postMessage({ id, error: error.message });
  }
}

function stepRequest(id, requestData) {
  const statement = statements.get(requestData.statementId);
  if (!statement) {
    postMessage({ id, error: 'Invalid statement ID: ' + requestData.statementId });
    return;
  }
  try {
    statement.reset();
    statement.clearBindings();
    for (let i = 0; i < requestData.bindings.length; i++) {
      statement.bind(i + 1, requestData.bindings[i]);
    }
    const rows = [];
    const columnTypes = [];
    while (statement.step()) {
      if (!columnTypes.length) {
        for (let i = 0; i < statement.columnCount; i++) {
          columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
        }
      }
      rows.push(statement.get([]));
    }
    postMessage({ id, data: { rows, columnTypes } });
  } catch (error) {
    postMessage({ id, error: error.message });
  }
}

function closeRequest(id, requestData) {
  try {
    if (requestData.statementId != null) {
      const statement = statements.get(requestData.statementId);
      if (statement) {
        statement.finalize();
        statements.delete(requestData.statementId);
      }
    }
    if (requestData.databaseId != null) {
      const database = databases.get(requestData.databaseId);
      if (database) {
        database.close();
        databases.delete(requestData.databaseId);
      }
    }
  } catch (error) {
    postMessage({ id, error: error.message });
  }
}

const commandMap = {
  open: openRequest,
  prepare: prepareRequest,
  step: stepRequest,
  close: closeRequest,
};

function handleMessage(e) {
  const requestMsg = e.data;
  if (requestMsg.data == null) {
    postMessage({ id: requestMsg.id, error: "Invalid request, missing 'data'." });
    return;
  }
  const command = requestMsg.data.cmd;
  const requestHandler = commandMap[command];
  if (requestHandler) {
    requestHandler(requestMsg.id, requestMsg.data);
  } else {
    postMessage({ id: requestMsg.id, error: "Invalid request, unknown command: '" + command + "'." });
  }
}

const messageQueue = [];
onmessage = (e) => {
  if (!sqlite3) {
    messageQueue.push(e);
  } else {
    handleMessage(e);
  }
};

sqlite3InitModule().then(instance => {
  sqlite3 = instance;
  while (messageQueue.length > 0) {
    handleMessage(messageQueue.shift());
  }
});
