/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.connector.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Blob;

import org.apache.log4j.Logger;
import org.apache.sqoop.common.SqoopException;
import org.apache.sqoop.connector.jdbc.configuration.FromJobConfiguration;
import org.apache.sqoop.connector.jdbc.configuration.LinkConfiguration;
import org.apache.sqoop.connector.jdbc.util.SqlTypesUtils;
import org.apache.sqoop.error.code.GenericJdbcConnectorError;
import org.apache.sqoop.job.etl.Extractor;
import org.apache.sqoop.job.etl.ExtractorContext;
import org.apache.sqoop.schema.Schema;
import org.apache.sqoop.schema.type.Column;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;


public class GenericJdbcExtractor extends Extractor<LinkConfiguration, FromJobConfiguration, GenericJdbcPartition> {

 public static final Logger LOG = Logger.getLogger(GenericJdbcExtractor.class);

 private long rowsRead = 0;
  @Override
  public void extract(ExtractorContext context, LinkConfiguration linkConfig, FromJobConfiguration fromJobConfig, GenericJdbcPartition partition) {
    GenericJdbcExecutor executor = new GenericJdbcExecutor(linkConfig);

    String query = context.getString(GenericJdbcConnectorConstants.CONNECTOR_JDBC_FROM_DATA_SQL);
    String conditions = partition.getConditions();
    query = query.replace(GenericJdbcConnectorConstants.SQL_CONDITIONS_TOKEN, conditions);
    LOG.info("Using query: " + query);

    rowsRead = 0;
    Schema schema = context.getSchema();
    Column[] schemaColumns = schema.getColumnsArray();
    try (Statement statement = executor.getConnection().createStatement(
            ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
         ResultSet resultSet = statement.executeQuery(query);) {
      ResultSetMetaData metaData = resultSet.getMetaData();
      int columnCount = metaData.getColumnCount();
      if (schemaColumns.length != columnCount) {
        throw new SqoopException(GenericJdbcConnectorError.GENERIC_JDBC_CONNECTOR_0021, schemaColumns.length + ":" + columnCount);
      }
      while (resultSet.next()) {
        Object[] array = new Object[columnCount];
        for (int i = 0; i < columnCount; i++) {
          if(resultSet.getObject(i + 1) == null) {
            array[i] = null ;
            continue;
          }
          // check type of the column
          Column schemaColumn = schemaColumns[i];
          switch (schemaColumn.getType()) {
            case DATE:
              // convert the sql date to JODA time as prescribed the Sqoop IDF spec
              array[i] = LocalDate.fromDateFields((java.sql.Date)resultSet.getObject(i + 1));
              break;
            case DATE_TIME:
              // convert the sql date time to JODA time as prescribed the Sqoop IDF spec
              array[i] = LocalDateTime.fromDateFields((java.sql.Timestamp)resultSet.getObject(i + 1));
              break;
            case TIME:
              // convert the sql time to JODA time as prescribed the Sqoop IDF spec
              array[i] = LocalTime.fromDateFields((java.sql.Time)resultSet.getObject(i + 1));
              break;
            case ARRAY:
              // use getArray() to get Object[] from java.sql.Array data type
              java.sql.Array objArray = (java.sql.Array) resultSet.getObject(i + 1);
              ResultSetMetaData arrayMeta = objArray.getResultSet().getMetaData();
              if (arrayMeta.getColumnCount() > 0)
                schemaColumns[i] = SqlTypesUtils.sqlTypeToSchemaType(arrayMeta.getColumnType(1), schemaColumn.getName(),
                    arrayMeta.getPrecision(1), arrayMeta.getScale(1));
              array[i] = objArray.getArray();
              break;
            case BINARY:
              array[i] = resultSet.getObject(i + 1);
              // use getBytes() to get byte[] from java.sql.Blob data type
              if (array[i] instanceof Blob) {
                Blob blob = (Blob) array[i];
                array[i] = blob.getBytes(1, (int) blob.length());
              }
            default:
              //for anything else
              array[i] = resultSet.getObject(i + 1);

          }
        }
        context.getDataWriter().writeArrayRecord(array);
        rowsRead++;
      }
    } catch (SQLException e) {
      throw new SqoopException(
          GenericJdbcConnectorError.GENERIC_JDBC_CONNECTOR_0004, e);

    } finally {
      executor.close();
    }
  }

  @Override
  public long getRowsRead() {
    return rowsRead;
  }

}
