import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { useTable } from 'react-table';
import './ViewProviders.css';

const ViewProviders = () => {
  const [providers, setProviders] = useState([]);

  useEffect(() => {
    // Fetch data from the backend
    const fetchProviders = async () => {
      try {
        const response = await axios.get('http://localhost:3000/view-providers');
        setProviders(response.data);
      } catch (error) {
        console.error('Error fetching providers:', error);
      }
    };

    fetchProviders();
  }, []);

  // Define the table columns
  const columns = React.useMemo(
    () => [
      {
        Header: 'Wallet Address',
        accessor: 'wallet_address',
      },
      {
        Header: 'Balance',
        accessor: 'balance',
      },
      {
        Header: 'Age',
        accessor: 'sharing_preferences.age',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Gender',
        accessor: 'sharing_preferences.gender',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Ethnicity',
        accessor: 'sharing_preferences.ethnicity',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Height',
        accessor: 'sharing_preferences.height',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Weight',
        accessor: 'sharing_preferences.weight',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Steps',
        accessor: 'sharing_preferences.steps',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Sleep Hours',
        accessor: 'sharing_preferences.sleep_hours',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Calories Consumed',
        accessor: 'sharing_preferences.calories_consumed',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Exercise Minutes',
        accessor: 'sharing_preferences.exercise_minutes',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Calories Expended',
        accessor: 'sharing_preferences.calories_expended',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Resting Heart Rate',
        accessor: 'sharing_preferences.resting_heart_rate',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Heart Rate',
        accessor: 'sharing_preferences.heart_rate',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
      {
        Header: 'Blood Pressure',
        accessor: 'sharing_preferences.blood_pressure',
        Cell: ({ value }) => <span style={{ color: value ? 'green' : 'gray' }}>{value ? 'TRUE' : 'FALSE'}</span>,
      },
    ],
    []
  );

  const { getTableProps, getTableBodyProps, headerGroups, rows, prepareRow } = useTable({ columns, data: providers });

  return (
    <div className="view-providers-container">
      <h2>View Providers</h2>
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

export default ViewProviders;
