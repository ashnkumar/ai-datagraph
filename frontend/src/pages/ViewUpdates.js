import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { useTable } from 'react-table';
import './ViewUpdates.css';

const ViewUpdates = () => {
  const [updates, setUpdates] = useState([]);

  useEffect(() => {
    const fetchUpdates = async () => {
      try {
        const response = await axios.get('http://localhost:3000/view-updates');
        // Convert the Firestore timestamp to a readable date format and handle undefined fields
        const processedUpdates = response.data.map(update => ({
          ...update,
          timestamp: new Date(update.timestamp.seconds * 1000).toLocaleString(), // Convert Firestore timestamp to readable date
          steps: update.private_data.steps || 'N/A', // Handle undefined fields
          sleep_hours: update.private_data.sleep_hours || 'N/A',
          calories_consumed: update.private_data.calories_consumed || 'N/A',
          exercise_minutes: update.private_data.exercise_minutes || 'N/A',
          calories_expended: update.private_data.calories_expended || 'N/A',
          resting_heart_rate: update.private_data.resting_heart_rate || 'N/A',
          heart_rate: update.private_data.heart_rate || 'N/A',
          blood_pressure: update.blood_pressure || 'N/A',
          usage_since_last_reward: update.usage_since_last_reward || 0 // Ensure usage field is present
        }));
        setUpdates(processedUpdates);
      } catch (error) {
        console.error('Error fetching updates:', error);
      }
    };

    fetchUpdates();
  }, []);

  const columns = React.useMemo(
    () => [
      {
        Header: 'Timestamp',
        accessor: 'timestamp', // This is now a string, so it can be rendered directly
      },
      {
        Header: 'Wallet Address',
        accessor: 'wallet_address',
      },
      {
        Header: 'Steps',
        accessor: 'steps',
      },
      {
        Header: 'Sleep Hours',
        accessor: 'sleep_hours',
      },
      {
        Header: 'Calories Consumed',
        accessor: 'calories_consumed',
      },
      {
        Header: 'Exercise Minutes',
        accessor: 'exercise_minutes',
      },
      {
        Header: 'Calories Expended',
        accessor: 'calories_expended',
      },
      {
        Header: 'Resting Heart Rate',
        accessor: 'resting_heart_rate',
      },
      {
        Header: 'Heart Rate',
        accessor: 'heart_rate',
      },
      {
        Header: 'Blood Pressure',
        accessor: 'blood_pressure',
      },
      {
        Header: 'Usage Since Last Reward',
        accessor: 'usage_since_last_reward',
      }
    ],
    []
  );

  const { getTableProps, getTableBodyProps, headerGroups, rows, prepareRow } = useTable({ columns, data: updates });

  return (
    <div className="view-updates-container">
      <h2>View Updates</h2>
      <table {...getTableProps()} className="table">
        <thead>
          {headerGroups.map((headerGroup) => (
            <tr {...headerGroup.getHeaderGroupProps()}>
              {headerGroup.headers.map((column) => (
                <th {...column.getHeaderProps()}>{column.render('Header')}</th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody {...getTableBodyProps()}>
          {rows.map((row) => {
            prepareRow(row);
            return (
              <tr {...row.getRowProps()}>
                {row.cells.map((cell) => (
                  <td {...cell.getCellProps()}>{cell.render('Cell')}</td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default ViewUpdates;
